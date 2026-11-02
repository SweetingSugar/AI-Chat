package com.example.aichat.ai.provider;

import com.example.aichat.ai.enums.AiProviderType;

import java.util.List;

public interface EmbeddingProvider {
    boolean supports(AiProviderType providerType);

    List<float[]> embed(AiModelConfigSnapshot snapshot, List<String> texts);
}
