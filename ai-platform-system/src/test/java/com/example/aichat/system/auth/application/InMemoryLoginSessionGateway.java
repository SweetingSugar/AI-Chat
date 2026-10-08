package com.example.aichat.system.auth.application;

class InMemoryLoginSessionGateway implements LoginSessionGateway {
    long loggedInUserId;
    @Override public String login(long userId) { loggedInUserId = userId; return "test-token"; }
    @Override public void logout() { loggedInUserId = 0; }
}
