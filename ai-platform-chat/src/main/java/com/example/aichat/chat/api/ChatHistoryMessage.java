package com.example.aichat.chat.api;

import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;

public record ChatHistoryMessage(Long id, ChatMessageRole role, String content, Long sequence,
                                 ChatMessageStatus status, Long modelId) {
}
