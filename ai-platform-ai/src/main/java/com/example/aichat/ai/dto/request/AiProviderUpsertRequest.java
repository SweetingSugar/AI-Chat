package com.example.aichat.ai.dto.request;

import com.example.aichat.ai.enums.AiProviderType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AiProviderUpsertRequest(
        @NotBlank @Size(max = 50) String providerCode,
        @NotBlank @Size(max = 100) String providerName,
        @NotNull AiProviderType providerType,
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 4096) String apiKey,
        @Min(1) @Max(120) Integer connectTimeout,
        @Min(1) @Max(600) Integer readTimeout,
        @Min(0) @Max(1) Integer status,
        @Min(0) Integer sort,
        @Size(max = 500) String remark) {
}
