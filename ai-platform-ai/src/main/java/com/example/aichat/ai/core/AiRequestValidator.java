package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageContentType;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class AiRequestValidator {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/gif");

    public void validate(AiChatRequest request) {
        if (request == null || request.messages().isEmpty())
            throw new IllegalArgumentException("AI messages must not be empty");
        for (AiMessageDTO message : request.messages()) {
            if (message == null || message.role() == null || message.content() == null || message.content().isBlank())
                throw new IllegalArgumentException("AI message content must not be blank");
            if (message.role() != com.example.aichat.ai.enums.AiMessageRole.USER && !message.contents().isEmpty()) {
                throw new IllegalArgumentException("Only user messages may contain file content");
            }
            for (AiMessageContentDTO content : message.contents()) {
                if (content.type() == AiMessageContentType.TEXT
                        && (content.text() == null || content.text().isBlank())) {
                    throw new IllegalArgumentException("Text content must not be blank");
                }
                if (content.type() == AiMessageContentType.IMAGE
                        && (!IMAGE_TYPES.contains(content.mediaType()) || content.data() == null || content.data().length == 0)) {
                    throw new BusinessException(ErrorCode.AI_FILE_TYPE_NOT_SUPPORTED,
                            "Image content is invalid");
                }
            }
        }
        AiGenerationOptions options = request.options();
        if (options != null && options.maxOutputTokens() != null && options.maxOutputTokens() <= 0)
            throw new IllegalArgumentException("maxOutputTokens must be positive");
    }
}
