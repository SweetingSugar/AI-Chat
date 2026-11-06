package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.service.KnowledgeBaseAdminService;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class AdminKnowledgeBaseController {
    private final KnowledgeBaseAdminService service;

    public AdminKnowledgeBaseController(KnowledgeBaseAdminService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("knowledge:read")
    public ApiResponse<PageResult<KnowledgeBaseAdminVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(page, size));
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    @SaCheckPermission("knowledge:read")
    public ApiResponse<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.documentPage(knowledgeBaseId, page, size));
    }

    @GetMapping("/{knowledgeBaseId}/documents/{documentId}/content")
    @SaCheckPermission("knowledge:read")
    public ApiResponse<KnowledgeDocumentContentVO> documentContent(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @PathVariable("documentId") Long documentId) {
        return ApiResponse.success(service.documentContent(knowledgeBaseId, documentId));
    }
}
