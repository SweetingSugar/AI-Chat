package com.example.aichat.knowledge.service.impl;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileMetadataVO;
import com.example.aichat.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseEmbeddingModelRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.aichat.knowledge.dto.KnowledgeTextDocumentRequest;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.KnowledgeBaseService;
import com.example.aichat.knowledge.vo.KnowledgeBaseVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {
    private final int maxTaskAttempts;

    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeIndexTaskRepository taskRepository;
    private final AiModelService modelService;
    private final FileService fileService;

    public KnowledgeBaseServiceImpl(KnowledgeBaseRepository baseRepository,
                                    KnowledgeDocumentRepository documentRepository,
                                    KnowledgeIndexTaskRepository taskRepository,
                                    AiModelService modelService,
                                    FileService fileService,
                                    @Value("${knowledge.vector.task-max-attempts:3}") int maxTaskAttempts) {
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.modelService = modelService;
        this.fileService = fileService;
        this.maxTaskAttempts = maxTaskAttempts;
    }

    @Override
    public PageResult<KnowledgeBaseVO> page(Long userId, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = baseRepository.countByUserId(userId);
        return new PageResult<>(baseRepository.findPageByUserId(userId, normalizedPage, normalizedSize)
                .stream().map(this::toBaseVO).toList(), total, normalizedPage, normalizedSize,
                pages(total, normalizedSize));
    }

    @Override
    public KnowledgeBaseVO create(Long userId, KnowledgeBaseCreateRequest request) {
        AiModel model = modelService.requireEnabledEmbedding(request.embeddingModelId());
        Long id = baseRepository.insert(userId, request.name().trim(), trimToNull(request.description()),
                model.id, configVersion(model));
        return toBaseVO(requireBase(userId, id));
    }

    @Override
    public KnowledgeBaseVO get(Long userId, Long knowledgeBaseId) {
        return toBaseVO(requireBase(userId, knowledgeBaseId));
    }

    @Override
    public KnowledgeBaseVO update(Long userId, Long knowledgeBaseId, KnowledgeBaseUpdateRequest request) {
        requireBase(userId, knowledgeBaseId);
        baseRepository.update(knowledgeBaseId, request.name().trim(), trimToNull(request.description()));
        return toBaseVO(requireBase(userId, knowledgeBaseId));
    }

    @Override
    public void delete(Long userId, Long knowledgeBaseId) {
        requireBase(userId, knowledgeBaseId);
        if (baseRepository.markDeleted(userId, knowledgeBaseId) == 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND);
        }
    }

    @Override
    public KnowledgeBaseVO changeEmbeddingModel(Long userId, Long knowledgeBaseId,
                                                KnowledgeBaseEmbeddingModelRequest request) {
        KnowledgeBaseRecord base = requireBase(userId, knowledgeBaseId);
        if (base.pendingIndexVersion() != null) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_REINDEXING);
        }
        AiModel model = modelService.requireEnabledEmbedding(request.embeddingModelId());
        long indexVersion = baseRepository.nextIndexVersion(knowledgeBaseId);
        baseRepository.updatePendingEmbeddingModel(knowledgeBaseId, model.id, configVersion(model), indexVersion);
        for (KnowledgeDocumentRecord document : documentRepository.findByKnowledgeBaseId(knowledgeBaseId)) {
            taskRepository.insert(knowledgeBaseId, document.id(), model.id, configVersion(model),
                    indexVersion, maxTaskAttempts);
        }
        if (documentRepository.countByKnowledgeBaseId(knowledgeBaseId) == 0) {
            baseRepository.switchActiveEmbedding(knowledgeBaseId, indexVersion);
        }
        return toBaseVO(requireBase(userId, knowledgeBaseId));
    }

    @Override
    public PageResult<KnowledgeDocumentVO> documentPage(Long userId, Long knowledgeBaseId,
                                                         int page, int size) {
        requireBase(userId, knowledgeBaseId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        return new PageResult<>(documentRepository.findPageByKnowledgeBaseId(knowledgeBaseId,
                        normalizedPage, normalizedSize).stream().map(this::toDocumentVO).toList(),
                total, normalizedPage, normalizedSize, pages(total, normalizedSize));
    }

    @Override
    public KnowledgeDocumentContentVO documentContent(Long userId, Long knowledgeBaseId, Long documentId) {
        KnowledgeDocumentRecord document = requireDocument(userId, knowledgeBaseId, documentId);
        if (document.sourceType() != KnowledgeDocumentSourceType.TEXT) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED);
        }
        if (!StringUtils.hasText(document.rawContent())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge document content is empty");
        }
        return new KnowledgeDocumentContentVO(document.id(), document.title(), document.rawContent());
    }

    @Override
    public KnowledgeDocumentVO addText(Long userId, Long knowledgeBaseId,
                                       KnowledgeTextDocumentRequest request) {
        KnowledgeBaseRecord base = requireBase(userId, knowledgeBaseId);
        Long documentId = documentRepository.insert(knowledgeBaseId, KnowledgeDocumentSourceType.TEXT,
                null, request.title().trim(), request.content(), base.activeIndexVersion());
        enqueue(knowledgeBaseId, documentId, base.embeddingModelId(), base.embeddingModelVersion(),
                base.activeIndexVersion());
        return toDocumentVO(requireDocument(userId, knowledgeBaseId, documentId));
    }

    @Override
    public KnowledgeDocumentVO addFile(Long userId, Long knowledgeBaseId, String title,
                                       MultipartFile file) {
        KnowledgeBaseRecord base = requireBase(userId, knowledgeBaseId);
        FileMetadataVO metadata = fileService.upload(file);
        String documentTitle = StringUtils.hasText(title) ? title.trim() : metadata.originalName();
        Long documentId = documentRepository.insert(knowledgeBaseId, KnowledgeDocumentSourceType.FILE,
                metadata.id(), documentTitle, null, base.activeIndexVersion());
        enqueue(knowledgeBaseId, documentId, base.embeddingModelId(), base.embeddingModelVersion(),
                base.activeIndexVersion());
        return toDocumentVO(requireDocument(userId, knowledgeBaseId, documentId));
    }

    @Override
    public void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId) {
        KnowledgeDocumentRecord document = requireDocument(userId, knowledgeBaseId, documentId);
        documentRepository.markDeleted(document.id());
    }

    @Override
    public void retryDocument(Long userId, Long knowledgeBaseId, Long documentId) {
        KnowledgeDocumentRecord document = requireDocument(userId, knowledgeBaseId, documentId);
        if (document.status() != KnowledgeDocumentStatus.FAILED) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_INDEX_FAILED,
                    "Only failed documents can be retried");
        }
        KnowledgeIndexTaskRecord task = taskRepository.findLatestFailedByDocumentId(document.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_INDEX_FAILED,
                        "No failed indexing task is available"));
        taskRepository.retry(task.id());
    }

    private void enqueue(Long baseId, Long documentId, Long modelId, Integer modelVersion,
                         Long indexVersion) {
        taskRepository.insert(baseId, documentId, modelId, modelVersion, indexVersion, maxTaskAttempts);
    }

    private KnowledgeBaseRecord requireBase(Long userId, Long id) {
        return baseRepository.findOwnedActive(userId, id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
    }

    private KnowledgeDocumentRecord requireDocument(Long userId, Long knowledgeBaseId, Long documentId) {
        requireBase(userId, knowledgeBaseId);
        KnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .filter(record -> !record.deleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
        if (!knowledgeBaseId.equals(document.knowledgeBaseId())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private KnowledgeBaseVO toBaseVO(KnowledgeBaseRecord base) {
        return new KnowledgeBaseVO(base.id(), base.name(), base.description(), base.embeddingModelId(),
                base.embeddingModelVersion(), base.pendingEmbeddingModelId(), base.pendingEmbeddingModelVersion(),
                base.activeIndexVersion(), base.pendingIndexVersion() != null, base.status(),
                base.createTime(), base.updateTime());
    }

    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentRecord document) {
        return new KnowledgeDocumentVO(document.id(), document.title(), document.sourceType(), document.fileId(),
                document.status(), document.indexVersion(), document.activeIndexVersion(), document.chunkCount(),
                document.errorCode(), document.errorMessage(), document.createTime(), document.updateTime());
    }

    private Integer configVersion(AiModel model) {
        return model.configVersion == null ? 1 : model.configVersion;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long pages(long total, int size) {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}
