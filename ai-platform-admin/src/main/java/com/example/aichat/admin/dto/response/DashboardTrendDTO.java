package com.example.aichat.admin.dto.response;

import java.time.LocalDate;

public record DashboardTrendDTO(
        LocalDate date,
        long newUsers,
        long activeUsers,
        long newSessions,
        long messages,
        long aiCalls,
        long successfulAiCalls,
        long totalTokens
) { }
