package com.example.aichat.ai.tool;

import java.util.List;

public interface AiToolProvider {
    AiToolContext prepare(Long userId, String requestId, List<String> toolCodes);
}
