package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.dto.response.AiProviderManagementDTO;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai/providers")
public class AiProviderController {
    private final AiProviderService providerService;

    public AiProviderController(AiProviderService providerService) {
        this.providerService = providerService;
    }

    @GetMapping
    @SaCheckPermission("ai:provider:read")
    public ApiResponse<List<AiProviderManagementDTO>> list() {
        return ApiResponse.success(providerService.listManagement());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:provider:read")
    public ApiResponse<AiProviderManagementDTO> get(@PathVariable Long id) {
        return ApiResponse.success(providerService.getManagementRequired(id));
    }

    @PostMapping
    @SaCheckPermission("ai:provider:create")
    public ApiResponse<AiProviderManagementDTO> create(@Valid @RequestBody AiProviderUpsertRequest request) {
        return ApiResponse.success(providerService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ai:provider:update")
    public ApiResponse<AiProviderManagementDTO> update(@PathVariable Long id,
                                                         @Valid @RequestBody AiProviderUpsertRequest request) {
        return ApiResponse.success(providerService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:provider:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        providerService.delete(id);
        return ApiResponse.success(null);
    }
}
