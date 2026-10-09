package com.example.aichat.system.security;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.system.entity.SysUser;
import com.example.aichat.system.mapper.UserMapper;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SaTokenCurrentUserProvider implements CurrentUserProvider {
    private final UserMapper userMapper;
    public SaTokenCurrentUserProvider(UserMapper userMapper) { this.userMapper = userMapper; }

    @Override
    public Optional<CurrentUser> findCurrentUser() {
        if (!StpUtil.isLogin()) return Optional.empty();
        Long id = StpUtil.getLoginIdAsLong();
        SysUser user = userMapper.selectById(id);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) return Optional.empty();
        return Optional.of(new CurrentUser(user.getId(), user.getUsername()));
    }
}
