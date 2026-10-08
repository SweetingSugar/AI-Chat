package com.example.aichat.system.auth.application;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.system.user.domain.User;
import com.example.aichat.system.user.domain.UserStatus;
import com.example.aichat.system.user.infrastructure.persistence.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthApplicationService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginSessionGateway loginSessionGateway;

    public AuthApplicationService(UserMapper userMapper, PasswordEncoder passwordEncoder, LoginSessionGateway loginSessionGateway) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginSessionGateway = loginSessionGateway;
    }

    public LoginResult login(LoginCommand command) {
        if (!StringUtils.hasText(command.username()) || !StringUtils.hasText(command.password())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        User user = userMapper.findByUsername(command.username());
        if (user == null || !passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Account is disabled");
        }
        return new LoginResult(user.getId(), user.getUsername(), loginSessionGateway.login(user.getId()));
    }

    public void logout() {
        loginSessionGateway.logout();
    }
}
