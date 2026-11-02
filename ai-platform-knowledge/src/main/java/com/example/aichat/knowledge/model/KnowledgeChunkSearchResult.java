package com.example.aichat.knowledge.model;

public record KnowledgeChunkSearchResult(Long chunkId, Long documentId, String title,
                                         String content, double distance) {
}
