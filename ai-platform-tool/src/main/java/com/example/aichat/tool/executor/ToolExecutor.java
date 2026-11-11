package com.example.aichat.tool.executor;

import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolType;

public interface ToolExecutor {
    ToolType toolType();
    ToolExecutionResult execute(ToolDefinition definition, ToolExecutionContext context);
}
