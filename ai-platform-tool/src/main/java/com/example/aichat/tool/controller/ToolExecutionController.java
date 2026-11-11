package com.example.aichat.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.service.ToolExecutionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolExecutionController {
    private final ToolExecutionService service;

    public ToolExecutionController(ToolExecutionService service) {
        this.service = service;
    }

    @PostMapping("/execute")
    @SaCheckPermission("tool:execute")
    public ApiResponse<ToolExecutionResult> execute(@Valid @RequestBody ToolExecuteRequest request) {
        return ApiResponse.success(service.execute(request));
    }
}
