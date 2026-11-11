package com.example.aichat.tool.executor;

import java.util.Map;

public record ToolExecutionContext(String requestId, Map<String, Object> arguments) {
    public ToolExecutionContext {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
