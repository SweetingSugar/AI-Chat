package com.example.aichat.system.auth.infrastructure;

import cn.dev33.satoken.stp.StpUtil;
import com.example.aichat.system.auth.application.LoginSessionGateway;
import org.springframework.stereotype.Component;

@Component
public class SaTokenLoginSessionGateway implements LoginSessionGateway {
    @Override
    public String login(long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    @Override
    public void logout() {
        StpUtil.logout();
    }
}
