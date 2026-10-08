package com.example.aichat.system.auth.application;

public interface LoginSessionGateway {
    String login(long userId);
    void logout();
}
