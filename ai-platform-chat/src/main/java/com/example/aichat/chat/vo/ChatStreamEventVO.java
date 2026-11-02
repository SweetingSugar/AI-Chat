package com.example.aichat.chat.vo;

import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.chat.enums.ChatStreamEventType;

import java.util.List;

public record ChatStreamEventVO(ChatStreamEventType type, Long taskId, String requestId, String delta,
                                Long assistantMessageId, Long modelId, String modelCode, AiTokenUsage usage,
                                String errorCode, String message, List<ChatKnowledgeSourceVO> sources) {
    public ChatStreamEventVO(ChatStreamEventType type, Long taskId, String requestId, String delta,
                             Long assistantMessageId, Long modelId, String modelCode, AiTokenUsage usage,
                             String errorCode, String message) {
        this(type, taskId, requestId, delta, assistantMessageId, modelId, modelCode, usage, errorCode, message,
                List.of());
    }

    public ChatStreamEventVO {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
