package com.example.aichat.ai.dto.request;

import java.util.List;

public record AiChatRequest(String requestId, Long userId, Long sessionId, Long messageId,
                            List<AiMessageDTO> messages, AiGenerationOptions options,
                            List<String> toolCodes) {
    public AiChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }

    public AiChatRequest(String requestId, Long userId, Long sessionId, Long messageId,
                         List<AiMessageDTO> messages, AiGenerationOptions options) {
        this(requestId, userId, sessionId, messageId, messages, options, List.of());
    }
}
