package com.example.aichat.ai.service.impl;

import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.exception.AiModelCallException;
import com.example.aichat.ai.mapper.AiProviderMapper;
import com.example.aichat.ai.service.AiProviderService;
import org.springframework.stereotype.Service;

@Service
public class AiProviderServiceImpl implements AiProviderService {
    private final AiProviderMapper mapper;

    public AiProviderServiceImpl(AiProviderMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiProvider requireEnabled(Long providerId) {
        AiProvider provider = mapper.selectById(providerId);
        if (provider == null || !Integer.valueOf(1).equals(provider.status)) {
            throw new AiModelCallException("AI provider is not enabled");
        }
        return provider;
    }
}
