package com.example.aichat.ai.dto.response;

import java.math.BigDecimal;

public record AiModelManagementDTO(
        Long id,
        Long providerId,
        String providerName,
        String modelCode,
        String modelName,
        String modelType,
        Integer contextWindow,
        Integer maxOutputTokens,
        BigDecimal temperature,
        BigDecimal topP,
        boolean supportStream,
        boolean supportVision,
        boolean defaultModel,
        Integer configVersion,
        Integer status,
        Integer sort,
        String remark) {
}
