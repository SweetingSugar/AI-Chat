package com.example.aichat.system.service;

import com.example.aichat.system.dto.PermissionDTO;
import com.example.aichat.system.entity.SysPermission;
import java.util.List;

public interface PermissionService {
    List<SysPermission> list();
    SysPermission getRequired(Long id);
    SysPermission create(PermissionDTO request);
    SysPermission update(Long id, PermissionDTO request);
    void delete(Long id);
}
