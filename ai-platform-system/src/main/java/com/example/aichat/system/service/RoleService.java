package com.example.aichat.system.service;

import com.example.aichat.system.dto.RoleDTO;
import com.example.aichat.system.entity.SysRole;

import java.util.Collection;
import java.util.List;

public interface RoleService {
    List<SysRole> list();

    SysRole getRequired(Long id);

    SysRole create(RoleDTO request);

    SysRole update(Long id, RoleDTO request);

    void delete(Long id);

    List<Long> listPermissionIds(Long roleId);

    void assignPermissions(Long roleId, Collection<Long> permissionIds);
}
