package com.example.aichat.ai.dto.response;

public record AiChatResponse(String requestId, Long providerId, Long modelId, String modelCode, AiTokenUsage usage) { }
