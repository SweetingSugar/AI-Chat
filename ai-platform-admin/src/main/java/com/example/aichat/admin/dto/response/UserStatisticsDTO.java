package com.example.aichat.admin.dto.response;

import java.util.List;

public record UserStatisticsDTO(
        long totalUsers,
        long enabledUsers,
        long disabledUsers,
        long newUsers,
        long activeUsers,
        List<UserTrendDTO> trend
) {
    public UserStatisticsDTO {
        trend = List.copyOf(trend);
    }
}
