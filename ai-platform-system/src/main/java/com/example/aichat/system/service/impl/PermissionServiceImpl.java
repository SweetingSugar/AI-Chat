package com.example.aichat.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.system.dto.PermissionDTO;
import com.example.aichat.system.entity.SysPermission;
import com.example.aichat.system.entity.SysRolePermission;
import com.example.aichat.system.mapper.PermissionMapper;
import com.example.aichat.system.mapper.RolePermissionMapper;
import com.example.aichat.system.mapper.UserRoleMapper;
import com.example.aichat.system.service.PermissionService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class PermissionServiceImpl implements PermissionService {
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserRoleMapper userRoleMapper;

    public PermissionServiceImpl(PermissionMapper permissionMapper, RolePermissionMapper rolePermissionMapper,
                                 UserRoleMapper userRoleMapper) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
    }

    @Override
    public List<SysPermission> list() { return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSort, SysPermission::getId)); }

    @Override
    public SysPermission getRequired(Long id) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Permission not found");
        return permission;
    }

    @Override
    @Transactional
    public SysPermission create(PermissionDTO request) {
        assertCodeAvailable(request.permissionCode(), null);
        SysPermission permission = copy(request, new SysPermission());
        permissionMapper.insert(permission);
        return permission;
    }

    @Override
    @Transactional
    public SysPermission update(Long id, PermissionDTO request) {
        SysPermission permission = getRequired(id);
        assertCodeAvailable(request.permissionCode(), id);
        copy(request, permission);
        permissionMapper.updateById(permission);
        invalidatePermissionUsers(id);
        return getRequired(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        getRequired(id);
        invalidatePermissionUsers(id);
        rolePermissionMapper.delete(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getPermissionId, id));
        permissionMapper.deleteById(id);
    }

    private void assertCodeAvailable(String code, Long excludedId) {
        if (!StringUtils.hasText(code)) return;
        LambdaQueryWrapper<SysPermission> query = new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getPermissionCode, code);
        if (excludedId != null) query.ne(SysPermission::getId, excludedId);
        if (permissionMapper.selectCount(query) > 0) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Permission code already exists");
    }

    private SysPermission copy(PermissionDTO request, SysPermission permission) {
        permission.setParentId(request.parentId() == null ? 0L : request.parentId());
        permission.setPermissionName(request.permissionName());
        permission.setPermissionCode(StringUtils.hasText(request.permissionCode()) ? request.permissionCode() : null);
        permission.setPermissionType(request.permissionType());
        permission.setPath(request.path());
        permission.setComponent(request.component());
        permission.setIcon(request.icon());
        permission.setSort(request.sort() == null ? 0 : request.sort());
        permission.setStatus(request.status() == null ? 1 : request.status());
        return permission;
    }

    private void invalidatePermissionUsers(Long permissionId) {
        List<SysRolePermission> relations = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getPermissionId, permissionId));
        relations.stream().map(SysRolePermission::getRoleId).distinct()
                .flatMap(roleId -> userRoleMapper.findUserIdsByRoleId(roleId).stream()).distinct().forEach(StpUtil::logout);
    }
}
