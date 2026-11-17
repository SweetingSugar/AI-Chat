package com.example.aichat.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.service.ToolQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolExecutionController {
    private final ToolExecutionService service;
    private final ToolQueryService queryService;

    public ToolExecutionController(ToolExecutionService service, ToolQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/available")
    public ApiResponse<List<AvailableToolView>> listAvailable() {
        return ApiResponse.success(queryService.listAvailable());
    }

    @PostMapping("/execute")
    @SaCheckPermission("tool:execute")
    public ApiResponse<ToolExecutionResult> execute(@Valid @RequestBody ToolExecuteRequest request) {
        return ApiResponse.success(service.execute(request));
    }
}
