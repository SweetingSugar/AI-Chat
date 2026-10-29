package com.example.aichat.ai.dto.response;

public record AiModelDTO(Long id, Long providerId, String providerName, String modelCode, String modelName,
                         Integer contextWindow, Integer maxOutputTokens, boolean supportStream,
                         boolean supportVision, boolean defaultModel) { }
