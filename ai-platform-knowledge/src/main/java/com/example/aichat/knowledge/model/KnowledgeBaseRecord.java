package com.example.aichat.knowledge.model;

import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;

import java.time.LocalDateTime;

public record KnowledgeBaseRecord(
        Long id,
        Long userId,
        String name,
        String description,
        Long embeddingModelId,
        Integer embeddingModelVersion,
        Long pendingEmbeddingModelId,
        Integer pendingEmbeddingModelVersion,
        Long pendingIndexVersion,
        Long activeIndexVersion,
        KnowledgeBaseStatus status,
        boolean deleted,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
