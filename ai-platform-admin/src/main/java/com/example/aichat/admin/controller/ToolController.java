package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.tool.dto.management.ToolManagementView;
import com.example.aichat.tool.dto.management.ToolStatusRequest;
import com.example.aichat.tool.dto.management.ToolUpsertRequest;
import com.example.aichat.tool.service.ToolManagementService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tools")
public class ToolController {
    private final ToolManagementService service;

    public ToolController(ToolManagementService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("tool:read")
    public ApiResponse<List<ToolManagementView>> list() {
        return ApiResponse.success(service.listManagement());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("tool:read")
    public ApiResponse<ToolManagementView> get(@PathVariable("id") Long id) {
        return ApiResponse.success(service.getManagementRequired(id));
    }

    @PostMapping
    @SaCheckPermission("tool:create")
    public ApiResponse<ToolManagementView> create(@Valid @RequestBody ToolUpsertRequest request) {
        return ApiResponse.success(service.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("tool:update")
    public ApiResponse<ToolManagementView> update(@PathVariable("id") Long id,
                                                   @Valid @RequestBody ToolUpsertRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tool:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/status")
    @SaCheckPermission("tool:update")
    public ApiResponse<ToolManagementView> updateStatus(@PathVariable("id") Long id,
                                                          @Valid @RequestBody ToolStatusRequest request) {
        return ApiResponse.success(service.updateStatus(id, request));
    }
}
