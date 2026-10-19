package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class AdminControllerMappingTest {
    @Test
    void dashboardEndpointsExposeExpectedPermissionAndPath() throws NoSuchMethodException {
        Method overview = AdminDashboardController.class.getMethod("overview",
                com.example.aichat.admin.dto.request.StatisticsQuery.class);
        Method trend = AdminDashboardController.class.getMethod("trend",
                com.example.aichat.admin.dto.request.StatisticsQuery.class);

        assertThat(overview.getAnnotation(GetMapping.class).value()).containsExactly("/overview");
        assertThat(trend.getAnnotation(GetMapping.class).value()).containsExactly("/trend");
        assertThat(overview.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("admin:dashboard:read");
        assertThat(trend.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("admin:dashboard:read");
    }

    @Test
    void statisticsEndpointsExposeExpectedPermissions() throws NoSuchMethodException {
        Method users = AdminStatisticsController.class.getMethod("users",
                com.example.aichat.admin.dto.request.StatisticsQuery.class);
        Method chat = AdminStatisticsController.class.getMethod("chat",
                com.example.aichat.admin.dto.request.StatisticsQuery.class);
        Method tokens = AdminStatisticsController.class.getMethod("tokens",
                com.example.aichat.admin.dto.request.StatisticsQuery.class, String.class, int.class, int.class);

        assertThat(users.getAnnotation(GetMapping.class).value()).containsExactly("/users");
        assertThat(chat.getAnnotation(GetMapping.class).value()).containsExactly("/chat");
        assertThat(tokens.getAnnotation(GetMapping.class).value()).containsExactly("/tokens");
        assertThat(users.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("admin:statistics:user:read");
        assertThat(chat.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("admin:statistics:chat:read");
        assertThat(tokens.getAnnotation(SaCheckPermission.class).value())
                .containsExactly("admin:statistics:token:read");
    }
}
