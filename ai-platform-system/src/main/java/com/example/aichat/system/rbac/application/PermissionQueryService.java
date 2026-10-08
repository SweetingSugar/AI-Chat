package com.example.aichat.system.rbac.application;

import com.example.aichat.system.rbac.infrastructure.persistence.PermissionMapper;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PermissionQueryService {
    private final PermissionMapper permissionMapper;
    public PermissionQueryService(PermissionMapper permissionMapper) { this.permissionMapper = permissionMapper; }
    public Set<String> permissionsOf(long userId) { return new LinkedHashSet<>(permissionMapper.selectPermissionCodesByUserId(userId)); }
    public Set<String> rolesOf(long userId) { return new LinkedHashSet<>(permissionMapper.selectRoleCodesByUserId(userId)); }
}
