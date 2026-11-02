package com.example.aichat.knowledge.mapper;

public record KnowledgeChunkRow(Long id, Long documentId, Integer chunkIndex,
                                String content, Integer estimatedTokens,
                                String metadataJson, Long indexVersion) {
}
