package com.example.aichat.file.exception;

import com.example.aichat.common.error.ErrorCode;

public enum FileErrorCode {
    FILE_EMPTY(ErrorCode.VALIDATION_ERROR, "File must not be empty"),
    FILE_TOO_LARGE(ErrorCode.VALIDATION_ERROR, "File exceeds the maximum allowed size"),
    FILE_TYPE_NOT_ALLOWED(ErrorCode.VALIDATION_ERROR, "File type is not allowed"),
    FILE_NAME_TOO_LONG(ErrorCode.VALIDATION_ERROR, "File name is too long"),
    FILE_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND, "File not found"),
    FILE_ACCESS_DENIED(ErrorCode.FORBIDDEN, "File access denied"),
    FILE_STORAGE_ERROR(ErrorCode.FILE_STORAGE_ERROR, "File storage service unavailable"),
    FILE_DELETE_FAILED(ErrorCode.FILE_DELETE_FAILED, "File deletion failed"),
    FILE_COUNT_EXCEEDED(ErrorCode.VALIDATION_ERROR, "Too many files");

    private final ErrorCode errorCode;
    private final String message;

    FileErrorCode(ErrorCode errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String message() {
        return message;
    }
}
