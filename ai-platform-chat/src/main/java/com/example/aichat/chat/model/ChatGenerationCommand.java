package com.example.aichat.chat.model;

import java.util.List;

public record ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                    String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                    Long existingUserMessageId, Long sourceMessageId,
                                    List<Long> knowledgeBaseIds, List<String> toolCodes, String systemPrompt) {
    public ChatGenerationCommand {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }

    public ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                 String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                 Long existingUserMessageId, Long sourceMessageId,
                                 List<Long> knowledgeBaseIds) {
        this(userId, sessionId, content, fileIds, modelId, requestId, temperature, topP, maxOutputTokens,
                existingUserMessageId, sourceMessageId, knowledgeBaseIds, List.of(), null);
    }

    public ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                 String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                 Long existingUserMessageId, Long sourceMessageId) {
        this(userId, sessionId, content, fileIds, modelId, requestId, temperature, topP, maxOutputTokens,
                existingUserMessageId, sourceMessageId, List.of(), List.of(), null);
    }

    public ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                 String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                 Long existingUserMessageId, Long sourceMessageId,
                                 List<Long> knowledgeBaseIds, List<String> toolCodes) {
        this(userId, sessionId, content, fileIds, modelId, requestId, temperature, topP, maxOutputTokens,
                existingUserMessageId, sourceMessageId, knowledgeBaseIds, toolCodes, null);
    }
}
