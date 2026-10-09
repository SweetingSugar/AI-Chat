package com.example.aichat.system.config;

import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.context.SaHolder;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {
    @Bean
    public SaServletFilter saServletFilter(ObjectMapper objectMapper) {
        return new SaServletFilter()
                .addInclude("/api/**")
                .addExclude("/api/auth/login", "/api/auth/register", "/api/auth/status")
                .setBeforeAuth(obj -> SaRouter.match("OPTIONS".equalsIgnoreCase(SaHolder.getRequest().getMethod())).stop())
                .setAuth(obj -> StpUtil.checkLogin())
                .setError(error -> writeUnauthorized(error, objectMapper));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/api/**");
    }

    private Object writeUnauthorized(Throwable error, ObjectMapper objectMapper) {
        try {
            HttpServletResponse response = (HttpServletResponse) cn.dev33.satoken.context.SaHolder.getResponse().getSource();
            response.setStatus(ErrorCode.UNAUTHENTICATED.httpStatus().value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(ErrorCode.UNAUTHENTICATED)));
        } catch (Exception ignored) {
        }
        return null;
    }
}
