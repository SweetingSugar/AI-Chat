package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.admin.dto.response.DashboardOverviewDTO;
import com.example.aichat.admin.dto.response.DashboardTrendDTO;
import com.example.aichat.admin.service.AdminStatisticsService;
import com.example.aichat.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {
    private final AdminStatisticsService statisticsService;

    public AdminDashboardController(AdminStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    @SaCheckPermission("admin:dashboard:read")
    public ApiResponse<DashboardOverviewDTO> overview(@Valid @ModelAttribute StatisticsQuery query) {
        return ApiResponse.success(statisticsService.overview(query));
    }

    @GetMapping("/trend")
    @SaCheckPermission("admin:dashboard:read")
    public ApiResponse<List<DashboardTrendDTO>> trend(@Valid @ModelAttribute StatisticsQuery query) {
        return ApiResponse.success(statisticsService.dashboardTrend(query));
    }
}
