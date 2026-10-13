package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.ai.dto.request.AiModelUpsertRequest;
import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.service.AiModelService;
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
@RequestMapping("/api/admin/ai/models")
public class AiModelController {
    private final AiModelService modelService;

    public AiModelController(AiModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    @SaCheckPermission("ai:model:read")
    public ApiResponse<List<AiModelManagementDTO>> list() {
        return ApiResponse.success(modelService.listManagement());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("ai:model:read")
    public ApiResponse<AiModelManagementDTO> get(@PathVariable Long id) {
        return ApiResponse.success(modelService.getManagementRequired(id));
    }

    @PostMapping
    @SaCheckPermission("ai:model:create")
    public ApiResponse<AiModelManagementDTO> create(@Valid @RequestBody AiModelUpsertRequest request) {
        return ApiResponse.success(modelService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("ai:model:update")
    public ApiResponse<AiModelManagementDTO> update(@PathVariable Long id,
                                                      @Valid @RequestBody AiModelUpsertRequest request) {
        return ApiResponse.success(modelService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("ai:model:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelService.delete(id);
        return ApiResponse.success(null);
    }
}
