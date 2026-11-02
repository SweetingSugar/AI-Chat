package com.example.aichat.knowledge.controller;

import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.knowledge.dto.KnowledgeBaseCreateRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseEmbeddingModelRequest;
import com.example.aichat.knowledge.dto.KnowledgeBaseUpdateRequest;
import com.example.aichat.knowledge.dto.KnowledgeTextDocumentRequest;
import com.example.aichat.knowledge.service.KnowledgeBaseService;
import com.example.aichat.knowledge.vo.KnowledgeBaseVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    private final KnowledgeBaseService service;
    private final CurrentUserProvider currentUserProvider;

    public KnowledgeBaseController(KnowledgeBaseService service,
                                   CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(@Valid @RequestBody KnowledgeBaseCreateRequest request) {
        return ApiResponse.success(service.create(currentUserProvider.requireCurrentUser().id(), request));
    }

    @GetMapping
    public ApiResponse<PageResult<KnowledgeBaseVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(currentUserProvider.requireCurrentUser().id(), page, size));
    }

    @GetMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseVO> get(@PathVariable Long knowledgeBaseId) {
        return ApiResponse.success(service.get(currentUserProvider.requireCurrentUser().id(), knowledgeBaseId));
    }

    @PutMapping("/{knowledgeBaseId}")
    public ApiResponse<KnowledgeBaseVO> update(@PathVariable Long knowledgeBaseId,
                                               @Valid @RequestBody KnowledgeBaseUpdateRequest request) {
        return ApiResponse.success(service.update(currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, request));
    }

    @DeleteMapping("/{knowledgeBaseId}")
    public ApiResponse<Void> delete(@PathVariable Long knowledgeBaseId) {
        service.delete(currentUserProvider.requireCurrentUser().id(), knowledgeBaseId);
        return ApiResponse.success(null);
    }

    @PutMapping("/{knowledgeBaseId}/embedding-model")
    public ApiResponse<KnowledgeBaseVO> changeEmbeddingModel(
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody KnowledgeBaseEmbeddingModelRequest request) {
        return ApiResponse.success(service.changeEmbeddingModel(
                currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, request));
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    public ApiResponse<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable Long knowledgeBaseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.documentPage(
                currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, page, size));
    }

    @PostMapping("/{knowledgeBaseId}/documents/text")
    public ApiResponse<KnowledgeDocumentVO> addText(
            @PathVariable Long knowledgeBaseId,
            @Valid @RequestBody KnowledgeTextDocumentRequest request) {
        return ApiResponse.success(service.addText(
                currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, request));
    }

    @PostMapping(value = "/{knowledgeBaseId}/documents/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<KnowledgeDocumentVO> addFile(
            @PathVariable Long knowledgeBaseId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "title", required = false) String title) {
        return ApiResponse.success(service.addFile(
                currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, title, file));
    }

    @DeleteMapping("/{knowledgeBaseId}/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long knowledgeBaseId,
                                            @PathVariable Long documentId) {
        service.deleteDocument(currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, documentId);
        return ApiResponse.success(null);
    }

    @PostMapping("/{knowledgeBaseId}/documents/{documentId}/retry")
    public ApiResponse<Void> retryDocument(@PathVariable Long knowledgeBaseId,
                                           @PathVariable Long documentId) {
        service.retryDocument(currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, documentId);
        return ApiResponse.success(null);
    }
}
