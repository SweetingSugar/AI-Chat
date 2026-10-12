package com.example.aichat.ai.dto.request;

import java.util.List;

public record AiChatRequest(String requestId, Long userId, Long sessionId, Long messageId,
                            List<AiMessageDTO> messages, AiGenerationOptions options) {
    public AiChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
