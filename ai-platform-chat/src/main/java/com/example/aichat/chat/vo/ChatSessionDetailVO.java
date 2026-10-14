package com.example.aichat.chat.vo;

import java.util.List;

public record ChatSessionDetailVO(ChatSessionVO session, List<ChatMessageVO> messages) {
    public ChatSessionDetailVO {
        messages = List.copyOf(messages);
    }
}
