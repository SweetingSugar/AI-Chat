package com.example.aichat.ai.dto.response;

public record AiUsageSummaryDTO(long calls, long successfulCalls, long failedCalls, long totalTokens) { }
