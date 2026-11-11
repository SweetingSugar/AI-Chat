package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.tool.dto.management.ToolInvocationQuery;
import com.example.aichat.tool.dto.management.ToolInvocationView;
import com.example.aichat.tool.service.ToolInvocationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tool/invocation-logs")
public class ToolInvocationController {
    private final ToolInvocationLogService service;

    public ToolInvocationController(ToolInvocationLogService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("tool:invocation:read")
    public ApiResponse<PageResult<ToolInvocationView>> page(@ModelAttribute ToolInvocationQuery query) {
        return ApiResponse.success(service.page(query));
    }
}
