package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.enums.AiProviderType;
import org.springframework.stereotype.Component;

@Component
public class QwenModelProvider extends OpenAiModelProvider {
    public QwenModelProvider(AiCredentialResolver credentialResolver) {
        super(credentialResolver);
    }

    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.QWEN;
    }
}
