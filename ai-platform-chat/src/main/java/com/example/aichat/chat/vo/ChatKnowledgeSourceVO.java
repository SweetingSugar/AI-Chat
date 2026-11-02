package com.example.aichat.chat.vo;

public record ChatKnowledgeSourceVO(Long knowledgeBaseId, Long documentId, Long chunkId,
                                    String title, String content, Double score, Integer rank) {
}
