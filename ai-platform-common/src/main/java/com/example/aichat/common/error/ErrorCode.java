package com.example.aichat.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permission denied"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed"),
    CHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_CONVERSATION_NOT_FOUND", "Conversation not found"),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI provider request failed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Internal server error");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
    public HttpStatus httpStatus() { return httpStatus; }
    public String code() { return code; }
    public String message() { return message; }
}
