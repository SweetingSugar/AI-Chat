package com.example.aichat.knowledge.model;

import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;

import java.time.LocalDateTime;

public record KnowledgeDocumentRecord(
        Long id,
        Long knowledgeBaseId,
        KnowledgeDocumentSourceType sourceType,
        Long fileId,
        String title,
        String rawContent,
        String contentHash,
        KnowledgeDocumentStatus status,
        Long indexVersion,
        Long activeIndexVersion,
        Integer chunkCount,
        String errorCode,
        String errorMessage,
        boolean deleted,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
