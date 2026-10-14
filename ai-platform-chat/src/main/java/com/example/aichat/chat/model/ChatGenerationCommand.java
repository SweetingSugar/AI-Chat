package com.example.aichat.chat.model;

public record ChatGenerationCommand(Long userId, Long sessionId, String content, Long modelId,
                                    String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                    Long existingUserMessageId, Long sourceMessageId) {
}
