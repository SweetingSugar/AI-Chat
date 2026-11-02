package com.example.aichat.ai.provider;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiModelType;
import com.example.aichat.ai.enums.AiProviderType;

import java.math.BigDecimal;
import java.util.Locale;

public record AiModelConfigSnapshot(Long providerId, String providerName, AiProviderType providerType, String baseUrl,
                                    String apiKeyCiphertext, Integer connectTimeout, Integer readTimeout, Long modelId,
                                    String modelCode, Integer contextWindow, Integer maxOutputTokens,
                                    BigDecimal temperature, BigDecimal topP, Integer configVersion,
                                    boolean supportVision, AiModelType modelType, Integer embeddingDimension) {
    public AiModelConfigSnapshot(Long providerId, String providerName, AiProviderType providerType, String baseUrl,
                                 String apiKeyCiphertext, Integer connectTimeout, Integer readTimeout, Long modelId,
                                 String modelCode, Integer contextWindow, Integer maxOutputTokens,
                                 BigDecimal temperature, BigDecimal topP, Integer configVersion,
                                 boolean supportVision) {
        this(providerId, providerName, providerType, baseUrl, apiKeyCiphertext, connectTimeout, readTimeout,
                modelId, modelCode, contextWindow, maxOutputTokens, temperature, topP, configVersion,
                supportVision, AiModelType.CHAT, null);
    }

    public static AiModelConfigSnapshot from(AiProvider provider, AiModel model) {
        AiModelType modelType = AiModelType.valueOf(model.modelType.trim().toUpperCase(Locale.ROOT));
        return new AiModelConfigSnapshot(provider.id, provider.providerName, provider.providerType, provider.baseUrl,
                provider.apiKeyCiphertext, provider.connectTimeout, provider.readTimeout, model.id, model.modelCode,
                model.contextWindow, model.maxOutputTokens, model.temperature, model.topP, model.configVersion,
                Integer.valueOf(1).equals(model.supportVision), modelType, model.embeddingDimension);
    }
}
