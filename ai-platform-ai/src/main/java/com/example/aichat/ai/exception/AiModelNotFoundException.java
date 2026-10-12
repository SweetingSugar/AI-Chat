package com.example.aichat.ai.exception;

public class AiModelNotFoundException extends AiException {
    public AiModelNotFoundException(Long modelId) {
        super("Enabled AI model not found: " + modelId);
    }
}
