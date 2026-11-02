package com.example.aichat.knowledge.model;

import com.example.aichat.knowledge.enums.KnowledgeIndexTaskStatus;

import java.time.LocalDateTime;

public record KnowledgeIndexTaskRecord(
        Long id,
        Long knowledgeBaseId,
        Long documentId,
        Long embeddingModelId,
        Integer modelVersion,
        Long indexVersion,
        KnowledgeIndexTaskStatus status,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime nextRetryAt,
        LocalDateTime lockedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String errorCode,
        String errorMessage,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
