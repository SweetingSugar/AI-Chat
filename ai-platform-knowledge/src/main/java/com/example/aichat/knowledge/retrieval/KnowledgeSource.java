package com.example.aichat.knowledge.retrieval;

public record KnowledgeSource(Long knowledgeBaseId, Long documentId, Long chunkId,
                              String title, String content, double score, int rank) {
}
