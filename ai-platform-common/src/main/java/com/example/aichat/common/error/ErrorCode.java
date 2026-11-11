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
    KNOWLEDGE_BASE_NOT_FOUND(HttpStatus.NOT_FOUND, "KNOWLEDGE_BASE_NOT_FOUND", "Knowledge base not found"),
    KNOWLEDGE_BASE_MODEL_INVALID(HttpStatus.BAD_REQUEST, "KNOWLEDGE_BASE_MODEL_INVALID", "Knowledge base embedding model is invalid"),
    KNOWLEDGE_BASE_REINDEXING(HttpStatus.CONFLICT, "KNOWLEDGE_BASE_REINDEXING", "Knowledge base is rebuilding its index"),
    KNOWLEDGE_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "KNOWLEDGE_DOCUMENT_NOT_FOUND", "Knowledge document not found"),
    KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY,
            "KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED", "Knowledge document content is not supported"),
    KNOWLEDGE_DOCUMENT_PARSE_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "KNOWLEDGE_DOCUMENT_PARSE_FAILED", "Knowledge document parsing failed"),
    KNOWLEDGE_DOCUMENT_INDEX_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "KNOWLEDGE_DOCUMENT_INDEX_FAILED", "Knowledge document indexing failed"),
    KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE", "Knowledge embedding model unavailable"),
    KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH", "Embedding vector dimension does not match model configuration"),
    KNOWLEDGE_RETRIEVAL_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_RETRIEVAL_FAILED", "Knowledge retrieval failed"),
    KNOWLEDGE_BASE_NOT_READY(HttpStatus.CONFLICT, "KNOWLEDGE_BASE_NOT_READY", "Knowledge base is not ready"),
    FILE_STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "FILE_STORAGE_ERROR", "File storage service unavailable"),
    FILE_DELETE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "FILE_DELETE_FAILED", "File deletion failed"),
    TOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"),
    TOOL_DISABLED(HttpStatus.CONFLICT, "TOOL_DISABLED", "Tool is disabled"),
    TOOL_STALE(HttpStatus.CONFLICT, "TOOL_STALE", "Tool discovery snapshot is stale"),
    TOOL_ARGUMENT_INVALID(HttpStatus.BAD_REQUEST, "TOOL_ARGUMENT_INVALID", "Tool arguments are invalid"),
    TOOL_CONFIG_INVALID(HttpStatus.BAD_REQUEST, "TOOL_CONFIG_INVALID", "Tool configuration is invalid"),
    TOOL_CONNECT_TIMEOUT(HttpStatus.BAD_GATEWAY, "TOOL_CONNECT_TIMEOUT", "Tool connection timed out"),
    TOOL_READ_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "TOOL_READ_TIMEOUT", "Tool response timed out"),
    TOOL_REMOTE_ERROR(HttpStatus.BAD_GATEWAY, "TOOL_REMOTE_ERROR", "Tool remote request failed"),
    TOOL_DISCOVERY_FAILED(HttpStatus.BAD_GATEWAY, "TOOL_DISCOVERY_FAILED", "MCP tool discovery failed"),
    TOOL_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, "TOOL_EXECUTION_FAILED", "Tool execution failed"),
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
