package com.example.aichat.tool.executor;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.service.McpServerManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class McpToolExecutor implements ToolExecutor {
    @Override
    public ToolType toolType() {
        return ToolType.MCP;
    }

    private final McpClientFactory clientFactory;
    private final McpServerManagementService serverService;
    private final ObjectMapper objectMapper;

    public McpToolExecutor(McpClientFactory clientFactory,
                           McpServerManagementService serverService,
                           ObjectMapper objectMapper) {
        this.clientFactory = clientFactory;
        this.serverService = serverService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolExecutionResult execute(ToolDefinition definition, ToolExecutionContext context) {
        if (definition.getToolType() != ToolType.MCP) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Tool is not an MCP tool");
        }
        if (definition.getAvailability() == ToolAvailability.STALE) {
            throw new BusinessException(ErrorCode.TOOL_STALE);
        }
        if (definition.getMcpServerId() == null || definition.getRemoteToolName() == null
                || definition.getRemoteToolName().isBlank()) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "MCP tool configuration is incomplete");
        }
        ToolMcpServer server = serverService.getRequired(definition.getMcpServerId());
        McpSyncClient client = null;
        long started = System.nanoTime();
        try {
            client = clientFactory.create(server);
            client.initialize();
            McpSchema.CallToolResult result = client.callTool(
                    new McpSchema.CallToolRequest(definition.getRemoteToolName(), context.arguments()));
            if (Boolean.TRUE.equals(result.isError())) {
                throw new BusinessException(ErrorCode.TOOL_REMOTE_ERROR, "MCP tool returned an error");
            }
            return new ToolExecutionResult(context.requestId(), definition.getToolCode(), ToolType.MCP,
                    true, 200, resultData(result), java.time.Duration.ofNanos(System.nanoTime() - started).toMillis());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ToolRemoteException(ErrorCode.TOOL_EXECUTION_FAILED, "MCP tool execution failed", exception);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private Object resultData(McpSchema.CallToolResult result) {
        if (result.structuredContent() != null) {
            return result.structuredContent();
        }
        List<McpSchema.Content> content = result.content() == null ? List.of() : result.content();
        if (content.size() == 1 && content.get(0) instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        List<Object> resultContent = new ArrayList<>();
        for (McpSchema.Content item : content) {
            resultContent.add(objectMapper.convertValue(item, Object.class));
        }
        return resultContent;
    }
}
