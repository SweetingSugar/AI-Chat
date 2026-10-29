package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class AiModelCapabilityValidator {
    public void validate(AiChatRequest request, AiModelConfigSnapshot snapshot) {
        boolean hasImage = request.messages().stream().anyMatch(AiMessageDTO::hasImageContent);
        if (hasImage && !snapshot.supportVision()) {
            throw new BusinessException(ErrorCode.AI_VISION_NOT_SUPPORTED);
        }
    }
}
