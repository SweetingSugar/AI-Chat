package com.example.aichat.ai.tool;

import java.util.Map;

@FunctionalInterface
public interface AiToolExecutor {
    AiToolExecutionResult execute(String toolCode, Map<String, Object> arguments);
}
