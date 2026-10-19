package com.example.aichat.admin.service.impl;

import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.admin.dto.response.ChatStatisticsDTO;
import com.example.aichat.admin.dto.response.ChatTrendDTO;
import com.example.aichat.admin.dto.response.DailyStatisticsRow;
import com.example.aichat.admin.dto.response.DashboardOverviewDTO;
import com.example.aichat.admin.dto.response.DashboardTrendDTO;
import com.example.aichat.admin.dto.response.TokenGroupDTO;
import com.example.aichat.admin.dto.response.TokenStatisticsDTO;
import com.example.aichat.admin.dto.response.UserStatisticsDTO;
import com.example.aichat.admin.dto.response.UserTrendDTO;
import com.example.aichat.admin.enums.TokenGroupBy;
import com.example.aichat.admin.mapper.AdminStatisticsMapper;
import com.example.aichat.admin.service.AdminStatisticsService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminStatisticsServiceImpl implements AdminStatisticsService {
    private final AdminStatisticsMapper mapper;

    public AdminStatisticsServiceImpl(AdminStatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DashboardOverviewDTO overview(StatisticsQuery query) {
        query.validate();
        Map<String, Object> users = mapper.selectUserOverview();
        Map<String, Object> sessions = mapper.selectSessionOverview();
        Map<String, Object> messages = mapper.selectMessageOverview(query.getFromDateTime(), query.getToDateTime());
        Map<String, Object> ai = mapper.selectAiOverview(query.getFromDateTime(), query.getToDateTime());
        long aiCalls = number(ai, "calls");
        return new DashboardOverviewDTO(
                number(users, "totalUsers"),
                number(users, "enabledUsers"),
                number(users, "disabledUsers"),
                mapper.countNewUsers(query.getFromDateTime(), query.getToDateTime()),
                mapper.countActiveUsers(query.getFromDateTime(), query.getToDateTime()),
                number(sessions, "totalSessions"),
                mapper.countNewSessions(query.getFromDateTime(), query.getToDateTime()),
                number(messages, "totalMessages"),
                number(messages, "userMessages"),
                number(messages, "assistantMessages"),
                aiCalls,
                number(ai, "successfulCalls"),
                number(ai, "failedCalls"),
                rate(number(ai, "successfulCalls"), aiCalls),
                number(ai, "inputTokens"),
                number(ai, "outputTokens"),
                number(ai, "totalTokens"));
    }

    @Override
    public List<DashboardTrendDTO> dashboardTrend(StatisticsQuery query) {
        query.validate();
        Map<LocalDate, DailyStatisticsRow> rows = dailyRows(query);
        return rows.entrySet().stream().map(entry -> {
            DailyStatisticsRow row = entry.getValue();
            return new DashboardTrendDTO(entry.getKey(), number(row.getNewUsers()), number(row.getActiveUsers()),
                    number(row.getNewSessions()), number(row.getMessages()), number(row.getAiCalls()),
                    number(row.getSuccessfulAiCalls()), number(row.getTotalTokens()));
        }).toList();
    }

    @Override
    public UserStatisticsDTO userStatistics(StatisticsQuery query) {
        query.validate();
        Map<String, Object> users = mapper.selectUserOverview();
        Map<LocalDate, DailyStatisticsRow> rows = dailyRows(query);
        List<UserTrendDTO> trend = rows.entrySet().stream()
                .map(entry -> new UserTrendDTO(entry.getKey(), number(entry.getValue().getNewUsers()),
                        number(entry.getValue().getActiveUsers())))
                .toList();
        return new UserStatisticsDTO(number(users, "totalUsers"), number(users, "enabledUsers"),
                number(users, "disabledUsers"), mapper.countNewUsers(query.getFromDateTime(), query.getToDateTime()),
                mapper.countActiveUsers(query.getFromDateTime(), query.getToDateTime()), trend);
    }

    @Override
    public ChatStatisticsDTO chatStatistics(StatisticsQuery query) {
        query.validate();
        Map<String, Object> sessions = mapper.selectSessionOverview();
        Map<String, Object> messages = mapper.selectMessageOverview(query.getFromDateTime(), query.getToDateTime());
        Map<String, Object> generation = mapper.selectGenerationOverview(query.getFromDateTime(), query.getToDateTime());
        Map<LocalDate, DailyStatisticsRow> rows = dailyRows(query);
        List<ChatTrendDTO> trend = rows.entrySet().stream().map(entry -> {
            DailyStatisticsRow row = entry.getValue();
            return new ChatTrendDTO(entry.getKey(), number(row.getNewSessions()), number(row.getMessages()),
                    number(row.getUserMessages()), number(row.getAssistantMessages()),
                    number(row.getCompletedMessages()), number(row.getFailedMessages()),
                    number(row.getGenerationTasks()), number(row.getSuccessfulGenerationTasks()));
        }).toList();
        long successful = number(generation, "successfulGenerationTasks");
        long denominator = successful + number(generation, "failedGenerationTasks")
                + number(generation, "interruptedGenerationTasks");
        return new ChatStatisticsDTO(number(sessions, "totalSessions"),
                mapper.countNewSessions(query.getFromDateTime(), query.getToDateTime()), number(messages, "totalMessages"),
                number(messages, "userMessages"), number(messages, "assistantMessages"),
                number(messages, "completedMessages"), number(messages, "failedMessages"),
                number(generation, "generationTasks"), successful,
                number(generation, "failedGenerationTasks"), number(generation, "interruptedGenerationTasks"),
                rate(successful, denominator), trend);
    }

    @Override
    public TokenStatisticsDTO tokenStatistics(StatisticsQuery query, String groupBy, int page, int size) {
        query.validate();
        if (page < 1 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page must be >= 1 and size must be 1..100");
        }
        Map<String, Object> summary = mapper.selectAiOverview(query.getFromDateTime(), query.getToDateTime());
        TokenGroupBy grouping = TokenGroupBy.from(groupBy);
        long offsetValue = (long) (page - 1) * size;
        if (offsetValue > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "page is too large");
        }
        int offset = (int) offsetValue;
        List<Map<String, Object>> groups = switch (grouping) {
            case DAY -> mapper.selectTokenByDay(query.getFromDateTime(), query.getToDateTime(), offset, size);
            case MODEL -> mapper.selectTokenByModel(query.getFromDateTime(), query.getToDateTime(), offset, size);
            case PROVIDER -> mapper.selectTokenByProvider(query.getFromDateTime(), query.getToDateTime(), offset, size);
            case USER -> mapper.selectTokenByUser(query.getFromDateTime(), query.getToDateTime(), offset, size);
        };
        List<TokenGroupDTO> groupDtos = groups.stream().map(this::toTokenGroup).toList();
        return new TokenStatisticsDTO(number(summary, "calls"), number(summary, "successfulCalls"),
                number(summary, "failedCalls"), number(summary, "cancelledCalls"), number(summary, "inputTokens"),
                number(summary, "outputTokens"), number(summary, "totalTokens"), number(summary, "estimatedTokens"),
                number(summary, "averageDurationMs"), groupDtos);
    }

    private Map<LocalDate, DailyStatisticsRow> dailyRows(StatisticsQuery query) {
        Map<LocalDate, DailyStatisticsRow> rows = new LinkedHashMap<>();
        LocalDate last = query.getToDateTime().minusNanos(1).toLocalDate();
        for (LocalDate date = query.getFrom(); !date.isAfter(last); date = date.plusDays(1)) {
            DailyStatisticsRow row = new DailyStatisticsRow();
            row.setStatDate(date.toString());
            rows.put(date, row);
        }
        mapper.selectNewUserTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 1));
        mapper.selectActiveUserTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 2));
        mapper.selectSessionTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 3));
        mapper.selectMessageTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 4));
        mapper.selectGenerationTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 5));
        mapper.selectAiTrend(query.getFromDateTime(), query.getToDateTime()).forEach(row -> apply(rows, row, 6));
        return rows;
    }

    private void apply(Map<LocalDate, DailyStatisticsRow> rows, DailyStatisticsRow source, int type) {
        if (source.getStatDate() == null) return;
        DailyStatisticsRow target = rows.get(LocalDate.parse(source.getStatDate()));
        if (target == null) return;
        switch (type) {
            case 1 -> target.setNewUsers(source.getNewUsers());
            case 2 -> target.setActiveUsers(source.getActiveUsers());
            case 3 -> target.setNewSessions(source.getNewSessions());
            case 4 -> {
                target.setMessages(source.getMessages());
                target.setUserMessages(source.getUserMessages());
                target.setAssistantMessages(source.getAssistantMessages());
                target.setCompletedMessages(source.getCompletedMessages());
                target.setFailedMessages(source.getFailedMessages());
            }
            case 5 -> {
                target.setGenerationTasks(source.getGenerationTasks());
                target.setSuccessfulGenerationTasks(source.getSuccessfulGenerationTasks());
            }
            case 6 -> {
                target.setAiCalls(source.getAiCalls());
                target.setSuccessfulAiCalls(source.getSuccessfulAiCalls());
                target.setTotalTokens(source.getTotalTokens());
            }
            default -> throw new IllegalArgumentException("Unknown daily statistic type: " + type);
        }
    }

    private TokenGroupDTO toTokenGroup(Map<String, Object> row) {
        return new TokenGroupDTO(String.valueOf(row.get("groupKey")), String.valueOf(row.get("groupName")),
                number(row, "calls"), number(row, "inputTokens"), number(row, "outputTokens"),
                number(row, "totalTokens"), number(row, "estimated") > 0);
    }

    private long number(Map<String, Object> row, String key) {
        return row == null || row.get(key) == null ? 0 : ((Number) row.get(key)).longValue();
    }

    private long number(Long value) {
        return value == null ? 0 : value;
    }

    private double rate(long numerator, long denominator) {
        return denominator == 0 ? 0D : (double) numerator / denominator;
    }
}
