package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;

public interface ToolExecutionService {
    ToolExecutionResult execute(ToolExecuteRequest request);
}
