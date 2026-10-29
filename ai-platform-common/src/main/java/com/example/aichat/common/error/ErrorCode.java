package com.example.aichat.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Permission denied"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource not found"),
    RESOURCE_CONFLICT(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", "Resource already exists"),
    CHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CHAT_CONVERSATION_NOT_FOUND", "Conversation not found"),
    AI_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AI_PROVIDER_ERROR", "AI provider request failed"),
    AI_FILE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AI_FILE_TYPE_NOT_SUPPORTED", "File type is not supported for AI analysis"),
    AI_FILE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "AI_FILE_PARSE_FAILED", "Unable to parse file for AI analysis"),
    AI_FILE_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "AI_FILE_CONTENT_EMPTY", "File content is empty"),
    AI_FILE_CONTENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "AI_FILE_CONTENT_TOO_LARGE", "File content exceeds the available context"),
    AI_VISION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AI_VISION_NOT_SUPPORTED", "The selected model does not support image input"),
    FILE_STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_ERROR", "File storage service unavailable"),
    FILE_DELETE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "FILE_DELETE_FAILED", "File deletion failed"),
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
