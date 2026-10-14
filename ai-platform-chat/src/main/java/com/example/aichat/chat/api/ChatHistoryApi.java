package com.example.aichat.chat.api;

public interface ChatHistoryApi {
    ChatHistoryQuery query(Long userId, Long sessionId);
}
