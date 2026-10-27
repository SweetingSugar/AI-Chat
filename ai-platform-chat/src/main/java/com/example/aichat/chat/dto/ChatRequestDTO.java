package com.example.aichat.chat.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRequestDTO(
        @NotBlank @Size(max = 100_000) String content,
        @Size(max = 10) List<Long> fileIds,
        Long modelId,
        @Size(max = 64) String requestId,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
        @Min(1) @Max(100_000) Integer maxOutputTokens) {
}
