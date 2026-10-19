package com.example.aichat.admin.dto.response;

import java.util.List;

public record ChatStatisticsDTO(
        long totalSessions,
        long newSessions,
        long totalMessages,
        long userMessages,
        long assistantMessages,
        long completedMessages,
        long failedMessages,
        long generationTasks,
        long successfulGenerationTasks,
        long failedGenerationTasks,
        long interruptedGenerationTasks,
        double generationSuccessRate,
        List<ChatTrendDTO> trend
) {
    public ChatStatisticsDTO {
        trend = List.copyOf(trend);
    }
}
