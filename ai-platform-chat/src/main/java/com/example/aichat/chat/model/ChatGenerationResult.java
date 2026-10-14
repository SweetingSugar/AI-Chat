package com.example.aichat.chat.model;

public record ChatGenerationResult(Long taskId, Long userId, Long userMessageId, Long assistantMessageId,
                                   String requestId, Long modelId, Long sourceMessageId) {
}
