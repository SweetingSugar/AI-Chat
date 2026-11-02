package com.example.aichat.knowledge.model;

import java.util.Map;

public record KnowledgeChunkRecord(
        Long id,
        Long documentId,
        Integer chunkIndex,
        String content,
        Integer estimatedTokens,
        Map<String, Object> metadata,
        Long indexVersion) {
    public KnowledgeChunkRecord(Long id, Long documentId, Integer chunkIndex, String content,
                                Integer estimatedTokens, Map<String, Object> metadata) {
        this(id, documentId, chunkIndex, content, estimatedTokens, metadata, null);
    }
}
