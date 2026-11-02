package com.example.aichat.knowledge.model;

public record KnowledgeChunkEmbeddingRecord(
        Long id,
        Long chunkId,
        Long knowledgeBaseId,
        Long embeddingModelId,
        Integer modelVersion,
        Integer dimension,
        Long indexVersion,
        float[] embedding) {
    public KnowledgeChunkEmbeddingRecord {
        embedding = embedding == null ? null : embedding.clone();
    }
}
