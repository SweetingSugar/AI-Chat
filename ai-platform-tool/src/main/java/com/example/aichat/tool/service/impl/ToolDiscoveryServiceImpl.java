package com.example.aichat.tool.service.impl;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.management.ToolDiscoveryResult;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.executor.McpClientFactory;
import com.example.aichat.tool.service.DiscoveredTool;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolDiscoveryPersistenceService;
import com.example.aichat.tool.service.ToolDiscoveryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ToolDiscoveryServiceImpl implements ToolDiscoveryService {
    private final McpServerManagementService serverService;
    private final McpClientFactory clientFactory;
    private final ToolDiscoveryPersistenceService persistenceService;
    private final ObjectMapper objectMapper;

    public ToolDiscoveryServiceImpl(McpServerManagementService serverService,
                                    McpClientFactory clientFactory,
                                    ToolDiscoveryPersistenceService persistenceService,
                                    ObjectMapper objectMapper) {
        this.serverService = serverService;
        this.clientFactory = clientFactory;
        this.persistenceService = persistenceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ToolDiscoveryResult discover(Long serverId) {
        ToolMcpServer server = serverService.getRequired(serverId);
        if (!Integer.valueOf(1).equals(server.getStatus())) {
            throw new BusinessException(ErrorCode.TOOL_DISABLED, "MCP server is disabled");
        }
        McpSyncClient client = null;
        try {
            client = clientFactory.create(server);
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<DiscoveredTool> tools = result.tools().stream().map(this::toDiscoveredTool).toList();
            return persistenceService.synchronize(server, tools);
        } catch (BusinessException exception) {
            persistenceService.markFailure(server, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            persistenceService.markFailure(server, "MCP discovery failed");
            throw new BusinessException(ErrorCode.TOOL_DISCOVERY_FAILED, "MCP tool discovery failed", exception);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private DiscoveredTool toDiscoveredTool(McpSchema.Tool tool) {
        Object schema = tool.inputSchema() == null ? null : tool.inputSchema();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> schemaMap = schema == null ? null
                : objectMapper.convertValue(schema, java.util.Map.class);
        return new DiscoveredTool(tool.name(),
                tool.title() == null || tool.title().isBlank() ? tool.name() : tool.title(),
                tool.description(), schemaMap);
    }
}
