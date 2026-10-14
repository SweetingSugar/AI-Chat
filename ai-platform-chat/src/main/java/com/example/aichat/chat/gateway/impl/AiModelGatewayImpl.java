package com.example.aichat.chat.gateway.impl;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.chat.gateway.AiModelGateway;
import org.springframework.stereotype.Component;

@Component
public class AiModelGatewayImpl implements AiModelGateway {
    private final AiModelService modelService;

    public AiModelGatewayImpl(AiModelService modelService) {
        this.modelService = modelService;
    }

    @Override
    public AiModel resolve(Long requestedModelId, Long sessionDefaultModelId) {
        if (requestedModelId != null) return modelService.requireEnabled(requestedModelId);
        if (sessionDefaultModelId != null) return modelService.requireEnabled(sessionDefaultModelId);
        return modelService.requireDefaultEnabled();
    }
}
