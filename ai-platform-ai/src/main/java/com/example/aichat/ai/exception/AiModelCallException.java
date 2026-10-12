package com.example.aichat.ai.exception;

public class AiModelCallException extends AiException {
    public AiModelCallException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiModelCallException(String message) {
        super(message);
    }
}
