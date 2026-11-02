package com.example.aichat.knowledge.repository;

import com.example.aichat.knowledge.mapper.KnowledgeIndexTaskMapper;
import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class KnowledgeIndexTaskRepository {
    private final KnowledgeIndexTaskMapper mapper;

    public KnowledgeIndexTaskRepository(KnowledgeIndexTaskMapper mapper) {
        this.mapper = mapper;
    }

    public Long insert(Long knowledgeBaseId, Long documentId, Long embeddingModelId, Integer modelVersion,
                       Long indexVersion, int maxAttempts) {
        return mapper.insert(knowledgeBaseId, documentId, embeddingModelId, modelVersion,
                indexVersion, maxAttempts);
    }

    public Optional<KnowledgeIndexTaskRecord> claimNext() {
        return Optional.ofNullable(mapper.claimNext());
    }

    public int markSuccess(Long taskId) {
        return mapper.markSuccess(taskId);
    }

    public int markFailed(Long taskId, String errorCode, String errorMessage) {
        return markFailed(taskId, errorCode, errorMessage, true);
    }

    public int markFailed(Long taskId, String errorCode, String errorMessage, boolean retryable) {
        if (retryable) {
            return mapper.markFailedRetryable(taskId, errorCode, errorMessage);
        }
        return mapper.markFailedTerminal(taskId, errorCode, errorMessage);
    }

    public int retry(Long taskId) {
        return mapper.retry(taskId);
    }

    public int recoverStale(LocalDateTime lockedBefore) {
        return mapper.recoverStale(lockedBefore);
    }

    public long countNotSuccessful(Long knowledgeBaseId, Long indexVersion) {
        return mapper.countNotSuccessful(knowledgeBaseId, indexVersion);
    }

    public Optional<KnowledgeIndexTaskRecord> findLatestFailedByDocumentId(Long documentId) {
        return Optional.ofNullable(mapper.findLatestFailedByDocumentId(documentId));
    }

    public Optional<KnowledgeIndexTaskRecord> findById(Long taskId) {
        return Optional.ofNullable(mapper.findById(taskId));
    }
}
