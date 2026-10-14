package com.example.aichat.chat.vo;

import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;

import java.time.LocalDateTime;

public record ChatMessageVO(Long id, Long sessionId, ChatMessageRole role, String content, Long sequence,
                            ChatMessageStatus status, Long generationTaskId, String requestId, Long modelId,
                            String errorCode, String errorMessage, LocalDateTime createTime, LocalDateTime updateTime) {
}
