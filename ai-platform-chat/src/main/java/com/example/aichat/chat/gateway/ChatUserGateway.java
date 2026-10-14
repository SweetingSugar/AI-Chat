package com.example.aichat.chat.gateway;

import com.example.aichat.common.security.CurrentUser;

public interface ChatUserGateway {
    CurrentUser requireCurrentUser();
}
