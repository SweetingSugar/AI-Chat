package com.example.aichat.admin.service;

import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.admin.dto.response.ChatStatisticsDTO;
import com.example.aichat.admin.dto.response.DashboardOverviewDTO;
import com.example.aichat.admin.dto.response.DashboardTrendDTO;
import com.example.aichat.admin.dto.response.TokenStatisticsDTO;
import com.example.aichat.admin.dto.response.UserStatisticsDTO;

import java.util.List;

public interface AdminStatisticsService {
    DashboardOverviewDTO overview(StatisticsQuery query);

    List<DashboardTrendDTO> dashboardTrend(StatisticsQuery query);

    UserStatisticsDTO userStatistics(StatisticsQuery query);

    ChatStatisticsDTO chatStatistics(StatisticsQuery query);

    TokenStatisticsDTO tokenStatistics(StatisticsQuery query, String groupBy, int page, int size);
}
