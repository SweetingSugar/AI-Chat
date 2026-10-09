package com.example.aichat.system.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SystemExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> notLogin(NotLoginException exception) {
        return ResponseEntity.status(ErrorCode.UNAUTHENTICATED.httpStatus())
                .body(ApiResponse.failure(ErrorCode.UNAUTHENTICATED));
    }

    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<ApiResponse<Void>> notPermission(NotPermissionException exception) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
                .body(ApiResponse.failure(ErrorCode.FORBIDDEN));
    }
}
