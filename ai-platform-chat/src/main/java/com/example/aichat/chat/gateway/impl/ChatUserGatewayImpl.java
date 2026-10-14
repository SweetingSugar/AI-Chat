package com.example.aichat.chat.gateway.impl;

import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import org.springframework.stereotype.Component;

@Component
public class ChatUserGatewayImpl implements ChatUserGateway {
    private final CurrentUserProvider currentUserProvider;

    public ChatUserGatewayImpl(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public CurrentUser requireCurrentUser() {
        return currentUserProvider.requireCurrentUser();
    }
}
