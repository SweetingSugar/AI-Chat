package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.aichat.ai.enums.AiProviderType;
import org.springframework.stereotype.Component;

@Component
public class QwenModelProvider extends OpenAiModelProvider {
    public QwenModelProvider(AiCredentialResolver credentialResolver) {
        super(credentialResolver);
    }

    @Autowired
    public QwenModelProvider(AiCredentialResolver credentialResolver, AiProperties properties,
                             ObjectMapper objectMapper) {
        super(credentialResolver, properties, objectMapper);
    }

    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.QWEN;
    }
}
