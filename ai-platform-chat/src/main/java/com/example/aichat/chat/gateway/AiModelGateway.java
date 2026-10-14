package com.example.aichat.chat.gateway;

import com.example.aichat.ai.entity.AiModel;

public interface AiModelGateway {
    AiModel resolve(Long requestedModelId, Long sessionDefaultModelId);
}
