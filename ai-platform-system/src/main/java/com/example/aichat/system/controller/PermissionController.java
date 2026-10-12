package com.example.aichat.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.system.dto.PermissionDTO;
import com.example.aichat.system.entity.SysPermission;
import com.example.aichat.system.service.PermissionService;
import jakarta.validation.Valid;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/permissions")
public class PermissionController {
    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping
    @SaCheckPermission("system:permission:read")
    public ApiResponse<List<SysPermission>> list() {
        return ApiResponse.success(permissionService.list());
    }

    @GetMapping("/{id}")
    @SaCheckPermission("system:permission:read")
    public ApiResponse<SysPermission> get(@PathVariable("id") Long id) {
        return ApiResponse.success(permissionService.getRequired(id));
    }

    @PostMapping
    @SaCheckPermission("system:permission:create")
    public ApiResponse<SysPermission> create(@Valid @RequestBody PermissionDTO request) {
        return ApiResponse.success(permissionService.create(request));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:permission:update")
    public ApiResponse<SysPermission> update(@PathVariable("id") Long id, @Valid @RequestBody PermissionDTO request) {
        return ApiResponse.success(permissionService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("system:permission:delete")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        permissionService.delete(id);
        return ApiResponse.success(null);
    }
}
