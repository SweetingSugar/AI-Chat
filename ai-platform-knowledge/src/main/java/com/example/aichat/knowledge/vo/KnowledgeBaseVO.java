package com.example.aichat.knowledge.vo;

import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;

import java.time.LocalDateTime;

public record KnowledgeBaseVO(Long id, String name, String description,
                              Long embeddingModelId, Integer embeddingModelVersion,
                              Long pendingEmbeddingModelId, Integer pendingEmbeddingModelVersion,
                              Long activeIndexVersion, boolean reindexing,
                              KnowledgeBaseStatus status, LocalDateTime createTime,
                              LocalDateTime updateTime) {
}
