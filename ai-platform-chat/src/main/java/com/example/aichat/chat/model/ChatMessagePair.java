package com.example.aichat.chat.model;

import com.example.aichat.chat.enums.ChatMessageRole;

import java.util.List;

public record ChatMessagePair(ChatMessageRole role, String content, List<Long> fileIds) {
    public ChatMessagePair {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
    }
}
