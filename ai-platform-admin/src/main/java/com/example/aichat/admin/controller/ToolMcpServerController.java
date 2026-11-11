package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.tool.dto.management.McpServerManagementView;
import com.example.aichat.tool.dto.management.McpServerStatusRequest;
import com.example.aichat.tool.dto.management.McpServerUpsertRequest;
import com.example.aichat.tool.dto.management.ToolDiscoveryResult;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolDiscoveryService;
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
@RequestMapping("/api/admin/tool/mcp-servers")
public class ToolMcpServerController {
    private final McpServerManagementService serverService;
    private final ToolDiscoveryService discoveryService;

    public ToolMcpServerController(McpServerManagementService serverService,
                                   ToolDiscoveryService discoveryService) {
        this.serverService = serverService;
        this.discoveryService = discoveryService;
    }

    @GetMapping
    @SaCheckPermission("tool:mcp-server:read")
    public ApiResponse<List<McpServerManagementView>> list() {
        return ApiResponse.success(serverService.listManagement());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("tool:mcp-server:read")
    public ApiResponse<McpServerManagementView> get(@PathVariable("id") Long id) {
        return ApiResponse.success(serverService.getManagementRequired(id));
    }

    @PostMapping
    @SaCheckPermission("tool:mcp-server:create")
    public ApiResponse<McpServerManagementView> create(@Valid @RequestBody McpServerUpsertRequest request) {
        return ApiResponse.success(serverService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("tool:mcp-server:update")
    public ApiResponse<McpServerManagementView> update(@PathVariable("id") Long id,
                                                        @Valid @RequestBody McpServerUpsertRequest request) {
        return ApiResponse.success(serverService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tool:mcp-server:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        serverService.delete(id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/status")
    @SaCheckPermission("tool:mcp-server:update")
    public ApiResponse<McpServerManagementView> updateStatus(@PathVariable("id") Long id,
                                                               @Valid @RequestBody McpServerStatusRequest request) {
        return ApiResponse.success(serverService.updateStatus(id, request));
    }

    @PostMapping("/{id}/discover")
    @SaCheckPermission("tool:mcp-server:discover")
    public ApiResponse<ToolDiscoveryResult> discover(@PathVariable("id") Long id) {
        return ApiResponse.success(discoveryService.discover(id));
    }
}
