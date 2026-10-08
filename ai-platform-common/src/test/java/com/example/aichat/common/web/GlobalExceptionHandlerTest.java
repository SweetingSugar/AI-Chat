package com.example.aichat.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    @Test
    void exposesStableBusinessErrorCode() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(new BusinessException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));
        assertThat(response.getStatusCode()).isEqualTo(ErrorCode.CHAT_CONVERSATION_NOT_FOUND.httpStatus());
        assertThat(response.getBody()).extracting(ApiResponse::code).isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
    }
}
