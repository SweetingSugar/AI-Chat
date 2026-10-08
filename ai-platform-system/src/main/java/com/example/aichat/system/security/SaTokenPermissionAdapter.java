package com.example.aichat.system.security;

import cn.dev33.satoken.stp.StpInterface;
import com.example.aichat.system.rbac.application.PermissionQueryService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SaTokenPermissionAdapter implements StpInterface {
    private final PermissionQueryService permissionQueryService;
    public SaTokenPermissionAdapter(PermissionQueryService permissionQueryService) { this.permissionQueryService = permissionQueryService; }
    @Override public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>(permissionQueryService.permissionsOf(Long.parseLong(loginId.toString())));
    }
    @Override public List<String> getRoleList(Object loginId, String loginType) {
        return new ArrayList<>(permissionQueryService.rolesOf(Long.parseLong(loginId.toString())));
    }
}
