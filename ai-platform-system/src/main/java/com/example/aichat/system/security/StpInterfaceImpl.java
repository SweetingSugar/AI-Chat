package com.example.aichat.system.security;

import cn.dev33.satoken.stp.StpInterface;
import com.example.aichat.system.mapper.PermissionMapper;
import com.example.aichat.system.mapper.RoleMapper;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class StpInterfaceImpl implements StpInterface {
    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;

    public StpInterfaceImpl(PermissionMapper permissionMapper, RoleMapper roleMapper) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return permissionMapper.findPermissionCodesByUserId(asLong(loginId));
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return roleMapper.findRoleCodesByUserId(asLong(loginId));
    }

    private Long asLong(Object loginId) {
        return Long.valueOf(String.valueOf(loginId));
    }
}
