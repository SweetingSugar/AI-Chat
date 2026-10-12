package com.example.aichat.ai.dto.response;

public record AiTokenUsage(Integer inputTokens, Integer outputTokens, Integer totalTokens, String source) { }
