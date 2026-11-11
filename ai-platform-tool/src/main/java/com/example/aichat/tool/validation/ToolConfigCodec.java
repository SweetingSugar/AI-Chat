package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ToolConfigCodec {
    private final ObjectMapper objectMapper;

    public ToolConfigCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(HttpToolConfig config) {
        if (config == null) {
            throw invalid("HTTP configuration is required");
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "HTTP configuration is invalid", exception);
        }
    }

    public HttpToolConfig decode(String json) {
        if (json == null || json.isBlank()) {
            throw invalid("HTTP configuration is required");
        }
        try {
            HttpToolConfig config = objectMapper.readValue(json, HttpToolConfig.class);
            if (config.url() == null || config.method() == null) {
                throw invalid("HTTP URL and method are required");
            }
            return config;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "HTTP configuration is invalid", exception);
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, message);
    }
}
