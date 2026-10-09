package com.example.aichat.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.system.dto.RoleDTO;
import com.example.aichat.system.entity.SysRole;
import com.example.aichat.system.entity.SysRolePermission;
import com.example.aichat.system.entity.SysUserRole;
import com.example.aichat.system.mapper.PermissionMapper;
import com.example.aichat.system.mapper.RoleMapper;
import com.example.aichat.system.mapper.RolePermissionMapper;
import com.example.aichat.system.mapper.UserRoleMapper;
import com.example.aichat.system.service.RoleService;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleServiceImpl implements RoleService {
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public RoleServiceImpl(RoleMapper roleMapper, PermissionMapper permissionMapper, UserRoleMapper userRoleMapper,
                           RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public List<SysRole> list() { return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getSort, SysRole::getId)); }

    @Override
    public SysRole getRequired(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found");
        return role;
    }

    @Override
    @Transactional
    public SysRole create(RoleDTO request) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, request.roleCode())) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Role code already exists");
        }
        SysRole role = copy(request, new SysRole());
        roleMapper.insert(role);
        return role;
    }

    @Override
    @Transactional
    public SysRole update(Long id, RoleDTO request) {
        SysRole role = getRequired(id);
        if (roleMapper.selectCount(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, request.roleCode()).ne(SysRole::getId, id)) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Role code already exists");
        }
        copy(request, role);
        roleMapper.updateById(role);
        invalidateRoleUsers(id);
        return getRequired(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getRequired(id);
        List<Long> userIds = userRoleMapper.findUserIdsByRoleId(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        roleMapper.deleteById(id);
        userIds.forEach(StpUtil::logout);
    }

    @Override
    @Transactional
    public void assignPermissions(Long roleId, Collection<Long> permissionIds) {
        getRequired(roleId);
        List<Long> ids = permissionIds == null ? List.of() : permissionIds.stream().distinct().toList();
        for (Long permissionId : ids) {
            if (permissionMapper.selectById(permissionId) == null) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Permission not found: " + permissionId);
            }
        }
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        for (Long permissionId : ids) {
            SysRolePermission relation = new SysRolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            rolePermissionMapper.insert(relation);
        }
        invalidateRoleUsers(roleId);
    }

    private SysRole copy(RoleDTO request, SysRole role) {
        role.setRoleName(request.roleName());
        role.setRoleCode(request.roleCode());
        role.setDescription(request.description());
        role.setSort(request.sort() == null ? 0 : request.sort());
        role.setStatus(request.status() == null ? 1 : request.status());
        return role;
    }

    private void invalidateRoleUsers(Long roleId) { userRoleMapper.findUserIdsByRoleId(roleId).forEach(StpUtil::logout); }
}
