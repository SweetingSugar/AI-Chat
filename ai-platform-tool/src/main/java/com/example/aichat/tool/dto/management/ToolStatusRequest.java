package com.example.aichat.tool.dto.management;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ToolStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {
}
