package com.example.aichat.knowledge.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;

public interface KnowledgeBaseAdminService {
    PageResult<KnowledgeBaseAdminVO> page(int page, int size);

    PageResult<KnowledgeDocumentVO> documentPage(Long knowledgeBaseId, int page, int size);

    KnowledgeDocumentContentVO documentContent(Long knowledgeBaseId, Long documentId);
}
