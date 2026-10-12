package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import org.springframework.stereotype.Component;

@Component
public class AiRequestValidator {
    public void validate(AiChatRequest request) {
        if (request == null || request.messages().isEmpty())
            throw new IllegalArgumentException("AI messages must not be empty");
        for (AiMessageDTO message : request.messages()) {
            if (message == null || message.role() == null || message.content() == null || message.content().isBlank())
                throw new IllegalArgumentException("AI message content must not be blank");
        }
        AiGenerationOptions options = request.options();
        if (options != null && options.maxOutputTokens() != null && options.maxOutputTokens() <= 0)
            throw new IllegalArgumentException("maxOutputTokens must be positive");
    }
}
