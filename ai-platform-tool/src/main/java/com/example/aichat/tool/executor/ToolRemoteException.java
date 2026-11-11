package com.example.aichat.tool.executor;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;

public class ToolRemoteException extends BusinessException {
    public ToolRemoteException(String message) {
        super(ErrorCode.TOOL_REMOTE_ERROR, message);
    }

    public ToolRemoteException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
