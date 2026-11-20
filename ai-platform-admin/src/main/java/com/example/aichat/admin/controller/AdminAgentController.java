package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {
    private final AgentService service;

    public AdminAgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("agent:read")
    public ApiResponse<PageResult<AgentAdminVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.adminPage(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("agent:read")
    public ApiResponse<AgentAdminVO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(service.adminGet(id));
    }
}
