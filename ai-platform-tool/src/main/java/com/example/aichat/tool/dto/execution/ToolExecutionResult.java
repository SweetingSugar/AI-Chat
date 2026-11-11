package com.example.aichat.tool.dto.execution;

import com.example.aichat.tool.enums.ToolType;

public record ToolExecutionResult(
        String requestId,
        String toolCode,
        ToolType toolType,
        boolean success,
        Integer statusCode,
        Object data,
        long durationMs) {
}
