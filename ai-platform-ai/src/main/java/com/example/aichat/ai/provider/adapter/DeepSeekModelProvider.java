package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.enums.AiProviderType;
import org.springframework.stereotype.Component;

@Component
public class DeepSeekModelProvider extends OpenAiModelProvider {
    public DeepSeekModelProvider(AiCredentialResolver credentialResolver) {
        super(credentialResolver);
    }

    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.DEEPSEEK;
    }
}
