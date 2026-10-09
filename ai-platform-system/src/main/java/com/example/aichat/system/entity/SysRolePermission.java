package com.example.aichat.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_role_permission")
public class SysRolePermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long roleId;
    private Long permissionId;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getRoleId() { return roleId; } public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getPermissionId() { return permissionId; } public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
}
