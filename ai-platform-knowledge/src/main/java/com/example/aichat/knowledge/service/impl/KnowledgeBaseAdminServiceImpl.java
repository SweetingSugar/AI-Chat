package com.example.aichat.knowledge.service.impl;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.service.KnowledgeBaseAdminService;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseAdminServiceImpl implements KnowledgeBaseAdminService {
    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;

    public KnowledgeBaseAdminServiceImpl(KnowledgeBaseRepository baseRepository,
                                         KnowledgeDocumentRepository documentRepository) {
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public PageResult<KnowledgeBaseAdminVO> page(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = baseRepository.countAll();
        return new PageResult<>(baseRepository.findPageAll(normalizedPage, normalizedSize)
                        .stream().map(this::toBaseVO).toList(),
                total, normalizedPage, normalizedSize, pages(total, normalizedSize));
    }

    @Override
    public PageResult<KnowledgeDocumentVO> documentPage(Long knowledgeBaseId, int page, int size) {
        requireBase(knowledgeBaseId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        return new PageResult<>(documentRepository.findPageByKnowledgeBaseId(
                                knowledgeBaseId, normalizedPage, normalizedSize)
                        .stream().map(this::toDocumentVO).toList(),
                total, normalizedPage, normalizedSize, pages(total, normalizedSize));
    }

    @Override
    public KnowledgeDocumentContentVO documentContent(Long knowledgeBaseId, Long documentId) {
        requireBase(knowledgeBaseId);
        KnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .filter(record -> !record.deleted())
                .filter(record -> knowledgeBaseId.equals(record.knowledgeBaseId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
        if (document.sourceType() != KnowledgeDocumentSourceType.TEXT) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED);
        }
        if (!StringUtils.hasText(document.rawContent())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge document content is empty");
        }
        return new KnowledgeDocumentContentVO(document.id(), document.title(), document.rawContent());
    }

    private KnowledgeBaseRecord requireBase(Long id) {
        return baseRepository.findNotDeletedById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
    }

    private KnowledgeBaseAdminVO toBaseVO(KnowledgeBaseRecord base) {
        return new KnowledgeBaseAdminVO(base.id(), base.userId(), base.name(), base.description(),
                base.embeddingModelId(), base.embeddingModelVersion(), base.pendingEmbeddingModelId(),
                base.pendingEmbeddingModelVersion(), base.activeIndexVersion(),
                base.pendingIndexVersion() != null, base.status(), base.createTime(), base.updateTime());
    }

    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentRecord document) {
        return new KnowledgeDocumentVO(document.id(), document.title(), document.sourceType(),
                document.fileId(), document.status(), document.indexVersion(),
                document.activeIndexVersion(), document.chunkCount(), document.errorCode(),
                document.errorMessage(), document.createTime(), document.updateTime());
    }

    private long pages(long total, int size) {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}
