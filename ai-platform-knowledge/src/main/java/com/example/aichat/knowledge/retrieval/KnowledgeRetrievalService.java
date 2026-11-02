package com.example.aichat.knowledge.retrieval;

import java.util.List;

public interface KnowledgeRetrievalService {
    KnowledgeContext retrieve(Long userId, List<Long> knowledgeBaseIds, String query);
}
