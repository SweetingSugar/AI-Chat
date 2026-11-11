package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolInvocationStatus;
import com.example.aichat.tool.enums.ToolType;

import java.time.LocalDateTime;

public record ToolInvocationView(
        Long id,
        String requestId,
        Long toolId,
        String toolCode,
        Long userId,
        ToolType toolType,
        ToolInvocationStatus status,
        Long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime startTime,
        LocalDateTime endTime,
        LocalDateTime createTime) {
}
