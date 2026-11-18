package com.example.aichat.agent.controller;

import com.example.aichat.agent.dto.AgentStatusRequest;
import com.example.aichat.agent.dto.AgentUpsertRequest;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentVO;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service;
    private final CurrentUserProvider currentUserProvider;

    public AgentController(AgentService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public ApiResponse<PageResult<AgentVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(currentUserProvider.requireCurrentUser().id(), page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentVO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(service.get(currentUserProvider.requireCurrentUser().id(), id));
    }

    @PostMapping
    public ApiResponse<AgentVO> create(@Valid @RequestBody AgentUpsertRequest request) {
        return ApiResponse.success(service.create(currentUserProvider.requireCurrentUser().id(), request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AgentVO> update(@PathVariable("id") Long id,
                                       @Valid @RequestBody AgentUpsertRequest request) {
        return ApiResponse.success(service.update(currentUserProvider.requireCurrentUser().id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        service.delete(currentUserProvider.requireCurrentUser().id(), id);
        return ApiResponse.success(null);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AgentVO> updateStatus(@PathVariable("id") Long id,
                                              @Valid @RequestBody AgentStatusRequest request) {
        return ApiResponse.success(service.updateStatus(
                currentUserProvider.requireCurrentUser().id(), id, request));
    }
}
