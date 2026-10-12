package com.example.aichat.ai.service;

import com.example.aichat.ai.entity.AiProvider;

public interface AiProviderService {
    AiProvider requireEnabled(Long providerId);
}
