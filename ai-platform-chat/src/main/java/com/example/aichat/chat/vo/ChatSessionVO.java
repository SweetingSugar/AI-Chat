package com.example.aichat.chat.vo;

import com.example.aichat.chat.enums.ChatSessionStatus;

import java.time.LocalDateTime;

public record ChatSessionVO(Long id, String title, Long defaultModelId, boolean pinned,
                            ChatSessionStatus status, LocalDateTime lastMessageTime,
                            LocalDateTime createTime, LocalDateTime updateTime) {
}
