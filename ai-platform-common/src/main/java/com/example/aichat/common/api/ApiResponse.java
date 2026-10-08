package com.example.aichat.common.api;

import com.example.aichat.common.error.ErrorCode;

public record ApiResponse<T>(boolean success, String code, String message, T data) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "SUCCESS", "OK", data);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.code(), errorCode.message(), null);
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.code(), message, null);
    }
}
