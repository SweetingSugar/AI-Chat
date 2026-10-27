package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {
    private final FileService service;

    public AdminFileController(FileService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("file:read")
    public ApiResponse<PageResult<FileMetadataVO>> page(@ModelAttribute FileQueryRequest request) {
        return ApiResponse.success(service.page(request));
    }

    @GetMapping("/{fileId}")
    @SaCheckPermission("file:read")
    public ApiResponse<FileMetadataVO> get(@PathVariable("fileId") Long fileId) {
        return ApiResponse.success(service.findByAdmin(fileId));
    }

    @DeleteMapping("/{fileId}")
    @SaCheckPermission("file:delete")
    public ApiResponse<Void> delete(@PathVariable("fileId") Long fileId) {
        service.deleteByAdmin(fileId);
        return ApiResponse.success(null);
    }
}
