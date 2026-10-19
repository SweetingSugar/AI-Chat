package com.example.aichat.admin.dto.response;

public record DashboardOverviewDTO(
        long totalUsers,
        long enabledUsers,
        long disabledUsers,
        long newUsers,
        long activeUsers,
        long totalSessions,
        long newSessions,
        long totalMessages,
        long userMessages,
        long assistantMessages,
        long aiCalls,
        long successfulAiCalls,
        long failedAiCalls,
        double aiSuccessRate,
        long inputTokens,
        long outputTokens,
        long totalTokens
) { }
