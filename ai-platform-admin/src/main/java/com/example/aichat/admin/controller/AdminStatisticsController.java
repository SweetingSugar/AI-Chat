package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.admin.dto.request.StatisticsQuery;
import com.example.aichat.admin.dto.response.ChatStatisticsDTO;
import com.example.aichat.admin.dto.response.TokenStatisticsDTO;
import com.example.aichat.admin.dto.response.UserStatisticsDTO;
import com.example.aichat.admin.service.AdminStatisticsService;
import com.example.aichat.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/statistics")
public class AdminStatisticsController {
    private final AdminStatisticsService statisticsService;

    public AdminStatisticsController(AdminStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/users")
    @SaCheckPermission("admin:statistics:user:read")
    public ApiResponse<UserStatisticsDTO> users(@Valid @ModelAttribute StatisticsQuery query) {
        return ApiResponse.success(statisticsService.userStatistics(query));
    }

    @GetMapping("/chat")
    @SaCheckPermission("admin:statistics:chat:read")
    public ApiResponse<ChatStatisticsDTO> chat(@Valid @ModelAttribute StatisticsQuery query) {
        return ApiResponse.success(statisticsService.chatStatistics(query));
    }

    @GetMapping("/tokens")
    @SaCheckPermission("admin:statistics:token:read")
    public ApiResponse<TokenStatisticsDTO> tokens(@Valid @ModelAttribute StatisticsQuery query,
                                                   @RequestParam(name = "groupBy", required = false) String groupBy,
                                                   @RequestParam(name = "page", defaultValue = "1") int page,
                                                   @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(statisticsService.tokenStatistics(query, groupBy, page, size));
    }
}
