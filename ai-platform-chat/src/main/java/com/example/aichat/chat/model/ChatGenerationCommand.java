package com.example.aichat.chat.model;

import java.util.List;

public record ChatGenerationCommand(Long userId, Long sessionId, String content, List<Long> fileIds, Long modelId,
                                    String requestId, Double temperature, Double topP, Integer maxOutputTokens,
                                    Long existingUserMessageId, Long sourceMessageId) {
}
