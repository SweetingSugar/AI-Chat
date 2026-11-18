package com.example.aichat.agent.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AgentVO(
        Long id,
        String name,
        String description,
        String systemPrompt,
        Long modelId,
        List<Long> knowledgeBaseIds,
        List<String> toolCodes,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
    public AgentVO {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
