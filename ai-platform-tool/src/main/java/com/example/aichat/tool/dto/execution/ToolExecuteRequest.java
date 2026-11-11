package com.example.aichat.tool.dto.execution;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ToolExecuteRequest(
        @NotBlank @Size(max = 100) String toolCode,
        @NotNull Map<String, Object> arguments,
        @Size(max = 64) String requestId) {
}
