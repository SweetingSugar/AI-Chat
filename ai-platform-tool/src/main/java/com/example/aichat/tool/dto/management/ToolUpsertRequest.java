package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ToolUpsertRequest(
        @NotBlank @Size(max = 100) String toolCode,
        @NotBlank @Size(max = 100) String toolName,
        @Size(max = 1000) String description,
        @NotNull ToolType toolType,
        @Valid HttpToolConfig httpConfig,
        @NotNull ToolAuthType authType,
        @Size(max = 100) String authHeaderName,
        @Size(max = 4096) String credential,
        Map<String, String> secretHeaders,
        @Min(0) @Max(1) Integer status) {
}
