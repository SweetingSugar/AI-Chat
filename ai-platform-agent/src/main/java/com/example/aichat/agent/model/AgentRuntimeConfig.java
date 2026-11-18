package com.example.aichat.agent.model;

import java.util.List;

public record AgentRuntimeConfig(
        Long agentId,
        Long modelId,
        String systemPrompt,
        List<Long> knowledgeBaseIds,
        List<String> toolCodes) {
    public AgentRuntimeConfig {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
