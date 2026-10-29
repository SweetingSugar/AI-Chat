package com.example.aichat.ai.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AiModelUpsertRequest(
        @NotNull Long providerId,
        @NotBlank @Size(max = 100) String modelCode,
        @NotBlank @Size(max = 100) String modelName,
        @NotBlank @Size(max = 32) String modelType,
        @Min(1) Integer contextWindow,
        @Min(1) Integer maxOutputTokens,
        @DecimalMin("0.0") @DecimalMax("2.0") BigDecimal temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") BigDecimal topP,
        @Min(0) @Max(1) Integer supportStream,
        @Min(0) @Max(1) Integer supportVision,
        @Min(0) @Max(1) Integer isDefault,
        @Min(0) @Max(1) Integer status,
        @Min(0) Integer sort,
        @Size(max = 500) String remark) {
}
