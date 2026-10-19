package com.example.aichat.admin.dto.response;

import java.util.List;

public record TokenStatisticsDTO(
        long calls,
        long successfulCalls,
        long failedCalls,
        long cancelledCalls,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long estimatedTokens,
        long averageDurationMs,
        List<TokenGroupDTO> groups
) {
    public TokenStatisticsDTO {
        groups = List.copyOf(groups);
    }
}
