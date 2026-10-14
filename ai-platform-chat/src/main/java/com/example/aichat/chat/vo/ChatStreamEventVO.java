package com.example.aichat.chat.vo;

import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.chat.enums.ChatStreamEventType;

public record ChatStreamEventVO(ChatStreamEventType type, Long taskId, String requestId, String delta,
                                Long assistantMessageId, Long modelId, String modelCode, AiTokenUsage usage,
                                String errorCode, String message) {
}
