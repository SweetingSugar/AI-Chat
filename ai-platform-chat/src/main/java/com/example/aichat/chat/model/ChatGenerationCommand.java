package com.example.aichat.chat.model;

import java.util.List;

public record ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                    String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                    Long existingUserMessageId, Long sourceMessageId,
                                    List<Long> knowledgeBaseIds) {
    public ChatGenerationCommand {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
    }

    public ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                 String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                 Long existingUserMessageId, Long sourceMessageId) {
        this(userId, sessionId, content, fileIds, modelId, requestId, temperature, topP, maxOutputTokens,
                existingUserMessageId, sourceMessageId, List.of());
    }
}
