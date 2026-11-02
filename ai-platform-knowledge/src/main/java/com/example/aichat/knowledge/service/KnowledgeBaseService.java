package com.example.aichat.knowledge.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseEmbeddingModelRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.aichat.knowledge.dto.KnowledgeTextDocumentRequest;
import com.example.aichat.knowledge.vo.KnowledgeBaseVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeBaseService {
    PageResult<KnowledgeBaseVO> page(Long userId, int page, int size);

    KnowledgeBaseVO create(Long userId, KnowledgeBaseCreateRequest request);

    KnowledgeBaseVO get(Long userId, Long knowledgeBaseId);

    KnowledgeBaseVO update(Long userId, Long knowledgeBaseId, KnowledgeBaseUpdateRequest request);

    void delete(Long userId, Long knowledgeBaseId);

    KnowledgeBaseVO changeEmbeddingModel(Long userId, Long knowledgeBaseId,
                                         KnowledgeBaseEmbeddingModelRequest request);

    PageResult<KnowledgeDocumentVO> documentPage(Long userId, Long knowledgeBaseId, int page, int size);

    KnowledgeDocumentVO addText(Long userId, Long knowledgeBaseId, KnowledgeTextDocumentRequest request);

    KnowledgeDocumentVO addFile(Long userId, Long knowledgeBaseId, String title, MultipartFile file);

    void deleteDocument(Long userId, Long knowledgeBaseId, Long documentId);

    void retryDocument(Long userId, Long knowledgeBaseId, Long documentId);
}
