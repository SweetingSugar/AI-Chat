package com.example.aichat.ai.dto.response;

import com.example.aichat.ai.enums.AiProviderType;

public record AiProviderManagementDTO(
        Long id,
        String providerCode,
        String providerName,
        AiProviderType providerType,
        String baseUrl,
        Integer connectTimeout,
        Integer readTimeout,
        Integer status,
        boolean hasApiKey,
        Integer credentialVersion,
        Integer sort,
        String remark) {
}
