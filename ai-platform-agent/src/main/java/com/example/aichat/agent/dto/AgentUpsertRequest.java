package com.example.aichat.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AgentUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 20_000) String systemPrompt,
        @NotNull Long modelId,
        @Size(max = 10) List<@NotNull Long> knowledgeBaseIds,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> toolCodes) {
    public AgentUpsertRequest {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
