package com.example.aichat.chat.service;

import com.example.aichat.chat.vo.ChatKnowledgeSourceVO;
import com.example.aichat.knowledge.retrieval.KnowledgeContext;

import java.util.List;
import java.util.Map;

public interface ChatKnowledgeService {
    void bindKnowledgeBases(Long messageId, List<Long> knowledgeBaseIds);

    List<Long> findKnowledgeBaseIds(Long messageId);

    void saveSources(Long messageId, KnowledgeContext context);

    Map<Long, List<ChatKnowledgeSourceVO>> findSourcesByMessageIds(List<Long> messageIds);
}
