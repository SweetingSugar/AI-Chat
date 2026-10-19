package com.example.aichat.admin.dto.response;

public record TokenGroupDTO(
        String key,
        String name,
        long calls,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        boolean estimated
) { }
