package com.example.aichat.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.system.dto.RegisterDTO;
import com.example.aichat.system.dto.UserUpdateDTO;
import com.example.aichat.system.entity.SysRole;
import com.example.aichat.system.entity.SysUser;
import com.example.aichat.system.entity.SysUserRole;
import com.example.aichat.system.mapper.RoleMapper;
import com.example.aichat.system.mapper.UserMapper;
import com.example.aichat.system.mapper.UserRoleMapper;
import com.example.aichat.system.security.PasswordService;
import com.example.aichat.system.service.UserService;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordService passwordService;

    public UserServiceImpl(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                           PasswordService passwordService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public SysUser register(RegisterDTO request) {
        if (userMapper.findByUsername(request.username()) != null) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Username already exists");
        }
        ensureUniqueContact(request.email(), request.phone(), null);
        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPassword(passwordService.encode(request.password()));
        user.setNickname(StringUtils.hasText(request.nickname()) ? request.nickname() : request.username());
        user.setEmail(emptyToNull(request.email()));
        user.setPhone(emptyToNull(request.phone()));
        user.setStatus(1);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    @Override
    public SysUser getRequired(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found");
        return user;
    }

    @Override
    public List<SysUser> list() { return userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId)); }

    @Override
    @Transactional
    public SysUser update(Long id, UserUpdateDTO request) {
        SysUser user = getRequired(id);
        ensureUniqueContact(request.email(), request.phone(), id);
        user.setNickname(emptyToNull(request.nickname()));
        user.setEmail(emptyToNull(request.email()));
        user.setPhone(emptyToNull(request.phone()));
        user.setAvatar(emptyToNull(request.avatar()));
        userMapper.updateById(user);
        return getRequired(id);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        SysUser user = getRequired(id);
        user.setStatus(status);
        userMapper.updateById(user);
        if (Integer.valueOf(0).equals(status)) StpUtil.logout(id);
    }

    @Override
    @Transactional
    public void assignRoles(Long userId, Collection<Long> roleIds) {
        getRequired(userId);
        List<Long> ids = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        for (Long roleId : ids) {
            SysRole role = roleMapper.selectById(roleId);
            if (role == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Role not found: " + roleId);
        }
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        for (Long roleId : ids) {
            SysUserRole relation = new SysUserRole();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            userRoleMapper.insert(relation);
        }
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = getRequired(userId);
        if (!passwordService.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (passwordService.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "New password must differ from current password");
        }
        user.setPassword(passwordService.encode(newPassword));
        userMapper.updateById(user);
        StpUtil.logout(userId);
    }

    private void ensureUniqueContact(String email, String phone, Long excludedUserId) {
        checkUnique(email, SysUser::getEmail, "Email already exists", excludedUserId);
        checkUnique(phone, SysUser::getPhone, "Phone already exists", excludedUserId);
    }

    private <T> void checkUnique(String value, com.baomidou.mybatisplus.core.toolkit.support.SFunction<SysUser, T> column,
                                 String message, Long excludedUserId) {
        if (!StringUtils.hasText(value)) return;
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<SysUser>().eq(column, value);
        if (excludedUserId != null) query.ne(SysUser::getId, excludedUserId);
        if (userMapper.selectCount(query) > 0) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, message);
    }

    private String emptyToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
