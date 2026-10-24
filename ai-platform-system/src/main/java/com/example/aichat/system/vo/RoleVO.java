package com.example.aichat.system.vo;

import com.example.aichat.system.entity.SysRole;
import java.time.LocalDateTime;
import java.util.List;

public record RoleVO(Long id, String roleName, String roleCode, String description,
                     Integer sort, Integer status, LocalDateTime createTime, LocalDateTime updateTime,
                     List<Long> permissionIds) {
    public static RoleVO from(SysRole role) {
        return new RoleVO(role.getId(), role.getRoleName(), role.getRoleCode(), role.getDescription(),
                role.getSort(), role.getStatus(), role.getCreateTime(), role.getUpdateTime(),
                List.of());
    }

    public RoleVO withPermissionIds(List<Long> permissionIds) {
        return new RoleVO(id, roleName, roleCode, description, sort, status, createTime, updateTime, permissionIds);
    }
}
