package com.example.aichat.chat.exception;

import com.example.aichat.common.error.ErrorCode;

public enum ChatErrorCode {
    SESSION_NOT_FOUND(ErrorCode.CHAT_CONVERSATION_NOT_FOUND),
    MESSAGE_NOT_FOUND(ErrorCode.RESOURCE_NOT_FOUND),
    GENERATION_IN_PROGRESS(ErrorCode.RESOURCE_CONFLICT),
    INVALID_REGENERATE_MESSAGE(ErrorCode.VALIDATION_ERROR);

    private final ErrorCode errorCode;

    ChatErrorCode(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
