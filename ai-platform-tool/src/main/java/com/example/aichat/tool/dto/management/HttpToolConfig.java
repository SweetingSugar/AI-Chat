package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolHttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record HttpToolConfig(
        @NotBlank @Size(max = 500) String url,
        @NotNull ToolHttpMethod method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        JsonNode bodyTemplate) {
}
