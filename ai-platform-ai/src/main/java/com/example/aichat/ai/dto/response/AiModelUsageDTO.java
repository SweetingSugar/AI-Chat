package com.example.aichat.ai.dto.response;

public record AiModelUsageDTO(Long modelId, String modelCode, long calls, long totalTokens) { }
