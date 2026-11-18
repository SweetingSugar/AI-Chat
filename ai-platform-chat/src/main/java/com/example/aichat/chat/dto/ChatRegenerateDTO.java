package com.example.aichat.chat.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ChatRegenerateDTO(@NotNull Long messageId, Long modelId, @Size(max = 64) String requestId,
                                @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
                                @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
                                @Min(1) @Max(100_000) Integer maxOutputTokens,
                                @Size(max = 20) List<@Size(max = 100) String> toolCodes,
                                Long agentId) {
    public ChatRegenerateDTO(Long messageId, Long modelId, String requestId,
                             Double temperature, Double topP, Integer maxOutputTokens,
                             List<String> toolCodes) {
        this(messageId, modelId, requestId, temperature, topP, maxOutputTokens, toolCodes, null);
    }
}
