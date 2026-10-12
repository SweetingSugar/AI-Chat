package com.example.aichat.ai.dto.request;

public record AiGenerationOptions(Long modelId, Double temperature, Double topP, Integer maxOutputTokens) { }
