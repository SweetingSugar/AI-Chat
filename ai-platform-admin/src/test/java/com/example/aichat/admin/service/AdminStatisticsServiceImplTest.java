package com.example.aichat.admin.service;

import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.admin.dto.response.DailyStatisticsRow;
import com.example.aichat.admin.dto.response.DashboardOverviewDTO;
import com.example.aichat.admin.dto.response.DashboardTrendDTO;
import com.example.aichat.admin.mapper.AdminStatisticsMapper;
import com.example.aichat.admin.service.impl.AdminStatisticsServiceImpl;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminStatisticsServiceImplTest {
    private AdminStatisticsService service;

    @BeforeEach
    void setUp() {
        service = new AdminStatisticsServiceImpl(new StubMapper());
    }

    @Test
    void dashboardTrendFillsDatesWithZeroes() {
        List<DashboardTrendDTO> result = service.dashboardTrend(query());

        assertEquals(2, result.size());
        assertEquals(0, result.get(0).messages());
        assertEquals(0, result.get(1).totalTokens());
    }

    @Test
    void overviewReturnsZeroSuccessRateWhenThereAreNoCalls() {
        DashboardOverviewDTO result = service.overview(query());

        assertEquals(0, result.aiSuccessRate());
        assertEquals(10, result.totalUsers());
        assertEquals(3, result.newUsers());
    }

    @Test
    void rejectsUnsupportedTokenGrouping() {
        assertThrows(BusinessException.class,
                () -> service.tokenStatistics(query(), "SQL", 1, 20));
    }

    private StatisticsQuery query() {
        return new StatisticsQuery(LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 3, 0, 0));
    }

    private static class StubMapper implements AdminStatisticsMapper {
        @Override public Map<String, Object> selectUserOverview() {
            return Map.of("totalUsers", 10L, "enabledUsers", 8L, "disabledUsers", 2L);
        }
        @Override public Long countNewUsers(LocalDateTime from, LocalDateTime to) { return 3L; }
        @Override public Long countActiveUsers(LocalDateTime from, LocalDateTime to) { return 4L; }
        @Override public Map<String, Object> selectSessionOverview() { return Map.of("totalSessions", 12L); }
        @Override public Long countNewSessions(LocalDateTime from, LocalDateTime to) { return 5L; }
        @Override public Map<String, Object> selectMessageOverview(LocalDateTime from, LocalDateTime to) {
            return Map.of("totalMessages", 20L, "userMessages", 10L, "assistantMessages", 10L,
                    "completedMessages", 9L, "failedMessages", 1L);
        }
        @Override public Map<String, Object> selectGenerationOverview(LocalDateTime from, LocalDateTime to) {
            return Map.of("generationTasks", 10L, "successfulGenerationTasks", 9L,
                    "failedGenerationTasks", 1L, "interruptedGenerationTasks", 0L);
        }
        @Override public Map<String, Object> selectAiOverview(LocalDateTime from, LocalDateTime to) {
            return Map.of("calls", 0L, "successfulCalls", 0L, "failedCalls", 0L, "cancelledCalls", 0L,
                    "inputTokens", 0L, "outputTokens", 0L, "totalTokens", 0L,
                    "estimatedTokens", 0L, "averageDurationMs", 0L);
        }
        @Override public List<DailyStatisticsRow> selectNewUserTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<DailyStatisticsRow> selectActiveUserTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<DailyStatisticsRow> selectSessionTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<DailyStatisticsRow> selectMessageTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<DailyStatisticsRow> selectGenerationTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<DailyStatisticsRow> selectAiTrend(LocalDateTime from, LocalDateTime to) { return List.of(); }
        @Override public List<Map<String, Object>> selectTokenByDay(LocalDateTime from, LocalDateTime to, int offset, int size) { return List.of(); }
        @Override public List<Map<String, Object>> selectTokenByModel(LocalDateTime from, LocalDateTime to, int offset, int size) { return List.of(); }
        @Override public List<Map<String, Object>> selectTokenByProvider(LocalDateTime from, LocalDateTime to, int offset, int size) { return List.of(); }
        @Override public List<Map<String, Object>> selectTokenByUser(LocalDateTime from, LocalDateTime to, int offset, int size) { return List.of(); }
    }
}
