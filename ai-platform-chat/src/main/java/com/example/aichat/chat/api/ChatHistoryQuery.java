package com.example.aichat.chat.api;

import java.util.List;

public record ChatHistoryQuery(Long sessionId, String title, Long defaultModelId,
                               List<ChatHistoryMessage> messages) {
    public ChatHistoryQuery {
        messages = List.copyOf(messages);
    }
}
