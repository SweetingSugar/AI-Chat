package com.example.aichat.system.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.dto.LoginDTO;
import com.example.aichat.system.entity.SysUser;
import com.example.aichat.system.mapper.UserMapper;
import com.example.aichat.system.security.PasswordService;
import com.example.aichat.system.service.AuthService;
import com.example.aichat.system.service.UserService;
import com.example.aichat.system.vo.LoginVO;
import com.example.aichat.system.vo.SessionVO;
import com.example.aichat.system.vo.UserVO;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {
    private final UserMapper userMapper;
    private final PasswordService passwordService;
    private final CurrentUserProvider currentUserProvider;
    private final UserService userService;

    public AuthServiceImpl(UserMapper userMapper, PasswordService passwordService,
                           CurrentUserProvider currentUserProvider, UserService userService) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
        this.currentUserProvider = currentUserProvider;
        this.userService = userService;
    }

    @Override
    @Transactional
    public LoginVO login(LoginDTO request, String clientIp) {
        SysUser user = userMapper.findByUsername(request.username());
        if (user == null || !passwordService.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Account is disabled");
        }
        StpUtil.login(user.getId());
        user.setLastLoginTime(LocalDateTime.now());
        user.setLastLoginIp(clientIp);
        userMapper.updateById(user);
        return new LoginVO(StpUtil.getTokenName(), StpUtil.getTokenValue(), UserVO.from(user));
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }

    @Override
    public void logoutAll() {
        StpUtil.logout(currentUserProvider.requireCurrentUser().id());
    }

    @Override
    public UserVO me() {
        Long userId = currentUserProvider.requireCurrentUser().id();
        return UserVO.from(userService.getRequired(userId))
                .withCodes(StpUtil.getRoleList(), StpUtil.getPermissionList());
    }

    @Override
    public SessionVO session() {
        if (!StpUtil.isLogin()) return new SessionVO(false, null, 0);
        return new SessionVO(true, StpUtil.getLoginIdAsLong(), StpUtil.getTokenTimeout());
    }
}
