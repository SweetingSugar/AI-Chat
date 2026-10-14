package com.example.aichat.chat.model;

import com.example.aichat.chat.enums.ChatMessageRole;

public record ChatMessagePair(ChatMessageRole role, String content) {
}
