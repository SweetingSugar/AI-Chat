package com.example.aichat.ai.tool;

public record AiToolExecutionResult(boolean success, Object data, String errorCode, String errorMessage) {
    public static AiToolExecutionResult success(Object data) {
        return new AiToolExecutionResult(true, data, null, null);
    }

    public static AiToolExecutionResult failure(String errorCode, String errorMessage) {
        return new AiToolExecutionResult(false, null, errorCode, errorMessage);
    }
}
