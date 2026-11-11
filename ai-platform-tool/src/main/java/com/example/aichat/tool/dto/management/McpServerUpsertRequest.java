package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolAuthType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record McpServerUpsertRequest(
        @NotBlank @Size(max = 100) String serverCode,
        @NotBlank @Size(max = 100) String serverName,
        @NotBlank @Size(max = 500) String endpointUrl,
        @NotNull ToolAuthType authType,
        @Size(max = 100) String authHeaderName,
        @Size(max = 4096) String credential,
        Map<String, String> secretHeaders,
        @Min(1) @Max(600) Integer connectTimeout,
        @Min(1) @Max(600) Integer readTimeout,
        @Min(0) @Max(1) Integer status) {
}
