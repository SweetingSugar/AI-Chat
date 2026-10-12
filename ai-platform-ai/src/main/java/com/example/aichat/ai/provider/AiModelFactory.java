package com.example.aichat.ai.provider;

import com.example.aichat.ai.exception.AiModelCallException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiModelFactory {
    private final List<ModelProvider> providers;

    public AiModelFactory(List<ModelProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ModelProvider get(AiModelConfigSnapshot snapshot) {
        return providers.stream().filter(provider -> provider.supports(snapshot.providerType())).findFirst()
                .orElseThrow(() -> new AiModelCallException("Unsupported AI provider: " + snapshot.providerType()));
    }
}
