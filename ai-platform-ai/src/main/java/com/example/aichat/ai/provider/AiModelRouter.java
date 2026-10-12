package com.example.aichat.ai.provider;

import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.ai.service.AiProviderService;
import org.springframework.stereotype.Component;

@Component
public class AiModelRouter {
    private final AiModelService modelService;
    private final AiProviderService providerService;

    public AiModelRouter(AiModelService modelService, AiProviderService providerService) {
        this.modelService = modelService;
        this.providerService = providerService;
    }

    public AiModelConfigSnapshot route(AiGenerationOptions options) {
        AiModel model = options != null && options.modelId() != null ? modelService.requireEnabled(options.modelId()) : modelService.requireDefaultEnabled();
        AiProvider provider = providerService.requireEnabled(model.providerId);
        return AiModelConfigSnapshot.from(provider, model);
    }
}
