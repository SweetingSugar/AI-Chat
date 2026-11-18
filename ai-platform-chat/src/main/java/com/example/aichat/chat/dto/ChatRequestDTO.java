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
        @Size(max = 10) List<Long> knowledgeBaseIds,
        Long modelId,
        @Size(max = 64) String requestId,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        @DecimalMin("0.0") @DecimalMax("1.0") Double topP,
        @Min(1) @Max(100_000) Integer maxOutputTokens,
        @Size(max = 20) List<@Size(max = 100) String> toolCodes,
        Long agentId) {
    public ChatRequestDTO {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }

    public ChatRequestDTO(String content, List<Long> fileIds, Long modelId, String requestId,
                          Double temperature, Double topP, Integer maxOutputTokens) {
        this(content, fileIds, List.of(), modelId, requestId, temperature, topP, maxOutputTokens,
                List.of(), null);
    }

    public ChatRequestDTO(String content, List<Long> fileIds, List<Long> knowledgeBaseIds, Long modelId,
                          String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                          List<String> toolCodes) {
        this(content, fileIds, knowledgeBaseIds, modelId, requestId, temperature, topP, maxOutputTokens,
                toolCodes, null);
    }
}
