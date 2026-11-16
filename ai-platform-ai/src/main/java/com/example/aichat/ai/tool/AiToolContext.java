package com.example.aichat.ai.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AiToolContext {
    private final Long userId;
    private final String requestId;
    private final List<AiToolDefinition> definitions;
    private final Map<String, AiToolDefinition> definitionsByCode;
    private final AiToolExecutor executor;

    public AiToolContext(Long userId, String requestId, List<AiToolDefinition> definitions,
                         AiToolExecutor executor) {
        this.userId = userId;
        this.requestId = requestId;
        this.definitions = List.copyOf(definitions);
        Map<String, AiToolDefinition> byCode = new LinkedHashMap<>();
        for (AiToolDefinition definition : this.definitions) {
            if (byCode.put(definition.code(), definition) != null) {
                throw new IllegalArgumentException("Duplicate AI tool code: " + definition.code());
            }
        }
        this.definitionsByCode = Map.copyOf(byCode);
        this.executor = executor;
    }

    public Long userId() {
        return userId;
    }

    public String requestId() {
        return requestId;
    }

    public List<AiToolDefinition> definitions() {
        return definitions;
    }

    public AiToolExecutionResult execute(String toolCode, Map<String, Object> arguments) {
        if (!definitionsByCode.containsKey(toolCode)) {
            return AiToolExecutionResult.failure("TOOL_NOT_FOUND", "Requested tool is not available");
        }
        return executor.execute(toolCode, arguments);
    }
}
