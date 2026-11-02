package com.example.aichat.knowledge.service.impl;

import com.example.aichat.ai.api.AiEmbeddingService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.content.KnowledgeDocumentContentReader;
import com.example.aichat.knowledge.content.KnowledgeTextNormalizer;
import com.example.aichat.knowledge.content.TextChunk;
import com.example.aichat.knowledge.content.TextChunker;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkEmbeddingRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeChunkEmbeddingRepository;
import com.example.aichat.knowledge.repository.KnowledgeChunkRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.KnowledgeIndexService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;

@Service
public class KnowledgeIndexServiceImpl implements KnowledgeIndexService {
    private final KnowledgeDocumentContentReader contentReader;
    private final KnowledgeTextNormalizer normalizer;
    private final TextChunker chunker;
    private final AiEmbeddingService embeddingService;
    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkEmbeddingRepository embeddingRepository;
    private final KnowledgeIndexTaskRepository taskRepository;

    public KnowledgeIndexServiceImpl(KnowledgeDocumentContentReader contentReader,
                                     TextChunker chunker,
                                     AiEmbeddingService embeddingService,
                                     KnowledgeBaseRepository baseRepository,
                                     KnowledgeDocumentRepository documentRepository,
                                     KnowledgeChunkRepository chunkRepository,
                                     KnowledgeChunkEmbeddingRepository embeddingRepository,
                                     KnowledgeIndexTaskRepository taskRepository) {
        this.contentReader = contentReader;
        this.normalizer = new KnowledgeTextNormalizer();
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.taskRepository = taskRepository;
    }

    @Override
    public void index(KnowledgeIndexTaskRecord task) {
        KnowledgeDocumentRecord document = null;
        try {
            document = documentRepository.findById(task.documentId())
                    .filter(record -> !record.deleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
            KnowledgeBaseRecord base = baseRepository.findById(task.knowledgeBaseId())
                    .filter(record -> !record.deleted())
                    .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
            documentRepository.markIndexing(document.id(), task.indexVersion());

            String content = normalizer.normalize(contentReader.read(document.id()));
            List<TextChunk> chunks = chunker.split(content);
            if (chunks.isEmpty()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                        "Knowledge document content is empty");
            }
            String contentHash = sha256(content);
            List<KnowledgeChunkRecord> persistedChunks = prepareChunks(document, chunks, task.indexVersion());
            List<String> texts = persistedChunks.stream().map(KnowledgeChunkRecord::content).toList();
            List<float[]> vectors = embeddingService.embed(task.embeddingModelId(), texts);
            if (vectors.size() != persistedChunks.size()) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH,
                        "Embedding result count does not match chunk count");
            }
            List<KnowledgeChunkEmbeddingRecord> embeddings = IntStream.range(0, vectors.size())
                    .mapToObj(index -> newEmbedding(persistedChunks.get(index), base, task, vectors.get(index)))
                    .toList();
            embeddingRepository.deleteByDocumentAndIndexVersion(document.id(), task.indexVersion());
            embeddingRepository.insertAll(embeddings);
            documentRepository.updateContentHash(document.id(), contentHash);
            documentRepository.markReady(document.id(), task.indexVersion(), persistedChunks.size());
            taskRepository.markSuccess(task.id());
            if (task.indexVersion().equals(base.pendingIndexVersion())
                    && taskRepository.countNotSuccessful(base.id(), task.indexVersion()) == 0) {
                baseRepository.switchActiveEmbedding(base.id(), task.indexVersion());
            }
        } catch (RuntimeException error) {
            String code = errorCode(error);
            String message = safeMessage(error);
            boolean preserveReady = document != null
                    && document.activeIndexVersion() != null
                    && !document.activeIndexVersion().equals(task.indexVersion());
            if (document != null) {
                documentRepository.markFailed(document.id(), code, message, preserveReady);
            }
            taskRepository.markFailed(task.id(), code, message);
        }
    }

    private List<KnowledgeChunkRecord> prepareChunks(KnowledgeDocumentRecord document,
                                                      List<TextChunk> chunks, Long indexVersion) {
        List<KnowledgeChunkRecord> existing = chunkRepository.findByDocumentIdAndIndexVersion(
                document.id(), indexVersion);
        List<String> existingContent = existing.stream().map(KnowledgeChunkRecord::content).toList();
        List<String> requestedContent = chunks.stream().map(TextChunk::content).toList();
        if (!existing.isEmpty() && existingContent.equals(requestedContent)) {
            return existing;
        }
        if (!existing.isEmpty()) {
            embeddingRepository.deleteByDocumentAndIndexVersion(document.id(), indexVersion);
            chunkRepository.deleteByDocumentAndIndexVersion(document.id(), indexVersion);
        }
        List<KnowledgeChunkRecord> records = chunks.stream()
                .map(chunk -> new KnowledgeChunkRecord(null, document.id(), chunk.index(), chunk.content(),
                        chunk.estimatedTokens(), chunk.metadata(), indexVersion))
                .toList();
        return chunkRepository.insertAll(records);
    }

    private KnowledgeChunkEmbeddingRecord newEmbedding(KnowledgeChunkRecord chunk,
                                                        KnowledgeBaseRecord base,
                                                        KnowledgeIndexTaskRecord task,
                                                        float[] vector) {
        return new KnowledgeChunkEmbeddingRecord(null, chunk.id(), base.id(), task.embeddingModelId(),
                task.modelVersion(), vector.length, task.indexVersion(), vector);
    }

    private String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String errorCode(RuntimeException error) {
        if (error instanceof BusinessException businessException) {
            return businessException.errorCode().code();
        }
        return ErrorCode.KNOWLEDGE_DOCUMENT_INDEX_FAILED.code();
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Knowledge document indexing failed";
        }
        String normalized = message.replaceAll("\\s+", " ");
        return normalized.substring(0, Math.min(900, normalized.length()));
    }
}
