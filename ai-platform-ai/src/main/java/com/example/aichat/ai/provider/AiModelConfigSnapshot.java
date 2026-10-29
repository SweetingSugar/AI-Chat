package com.example.aichat.ai.provider;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;

import java.math.BigDecimal;

public record AiModelConfigSnapshot(Long providerId, String providerName, AiProviderType providerType, String baseUrl,
                                    String apiKeyCiphertext, Integer connectTimeout, Integer readTimeout, Long modelId,
                                    String modelCode, Integer contextWindow, Integer maxOutputTokens,
                                    BigDecimal temperature, BigDecimal topP, Integer configVersion,
                                    boolean supportVision) {
    public static AiModelConfigSnapshot from(AiProvider provider, AiModel model) {
        return new AiModelConfigSnapshot(provider.id, provider.providerName, provider.providerType, provider.baseUrl,
                provider.apiKeyCiphertext, provider.connectTimeout, provider.readTimeout, model.id, model.modelCode,
                model.contextWindow, model.maxOutputTokens, model.temperature, model.topP, model.configVersion,
                Integer.valueOf(1).equals(model.supportVision));
    }
}
