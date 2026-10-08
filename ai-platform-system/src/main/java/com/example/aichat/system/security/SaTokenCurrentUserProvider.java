package com.example.aichat.system.security;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.user.domain.User;
import com.example.aichat.system.user.infrastructure.persistence.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SaTokenCurrentUserProvider implements CurrentUserProvider {
    private final UserMapper userMapper;
    public SaTokenCurrentUserProvider(UserMapper userMapper) { this.userMapper = userMapper; }
    @Override
    public Optional<CurrentUser> findCurrentUser() {
        if (!StpUtil.isLogin()) return Optional.empty();
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        return user == null ? Optional.empty() : Optional.of(new CurrentUser(user.getId(), user.getUsername()));
    }
}
