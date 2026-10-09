package com.example.aichat.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.system.dto.IdSetDTO;
import com.example.aichat.system.dto.RoleDTO;
import com.example.aichat.system.entity.SysRole;
import com.example.aichat.system.service.RoleService;
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
@RequestMapping("/api/system/roles")
public class RoleController {
    private final RoleService roleService;
    public RoleController(RoleService roleService) { this.roleService = roleService; }
    @GetMapping @SaCheckPermission("system:role:read")
    public ApiResponse<List<SysRole>> list() { return ApiResponse.success(roleService.list()); }
    @GetMapping("/{id}") @SaCheckPermission("system:role:read")
    public ApiResponse<SysRole> get(@PathVariable Long id) { return ApiResponse.success(roleService.getRequired(id)); }
    @PostMapping @SaCheckPermission("system:role:create")
    public ApiResponse<SysRole> create(@Valid @RequestBody RoleDTO request) { return ApiResponse.success(roleService.create(request)); }
    @PutMapping("/{id}") @SaCheckPermission("system:role:update")
    public ApiResponse<SysRole> update(@PathVariable Long id, @Valid @RequestBody RoleDTO request) { return ApiResponse.success(roleService.update(id, request)); }
    @DeleteMapping("/{id}") @SaCheckPermission("system:role:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) { roleService.delete(id); return ApiResponse.success(null); }
    @PutMapping("/{id}/permissions") @SaCheckPermission("system:role:assign-permission")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody IdSetDTO request) {
        roleService.assignPermissions(id, request.ids()); return ApiResponse.success(null);
    }
}
