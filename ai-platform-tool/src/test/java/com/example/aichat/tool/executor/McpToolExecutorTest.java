package com.example.aichat.tool.executor;

import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.service.McpServerManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolExecutorTest {
    @Test
    void callsRemoteToolAndReturnsStructuredContent() {
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpServerManagementService serverService = mock(McpServerManagementService.class);
        McpSyncClient client = mock(McpSyncClient.class);
        ToolMcpServer server = new ToolMcpServer();
        server.setId(3L);
        server.setStatus(1);
        when(serverService.getRequired(3L)).thenReturn(server);
        when(clientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("done")), false,
                Map.of("value", 42), Map.of()));

        ToolDefinition definition = new ToolDefinition();
        definition.setToolCode("weather");
        definition.setToolType(ToolType.MCP);
        definition.setAvailability(ToolAvailability.AVAILABLE);
        definition.setMcpServerId(3L);
        definition.setRemoteToolName("weather_query");
        definition.setAuthType(ToolAuthType.NONE);

        McpToolExecutor executor = new McpToolExecutor(clientFactory, serverService, new ObjectMapper());
        ToolExecutionResult result = executor.execute(definition,
                new ToolExecutionContext("req-4", Map.of("city", "Shanghai")));

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.data()).isEqualTo(Map.of("value", 42));
        verify(client).initialize();
        verify(client).close();
    }

    @Test
    void convertsTextContentWhenStructuredContentIsAbsent() {
        McpClientFactory clientFactory = mock(McpClientFactory.class);
        McpServerManagementService serverService = mock(McpServerManagementService.class);
        McpSyncClient client = mock(McpSyncClient.class);
        ToolMcpServer server = new ToolMcpServer();
        server.setId(3L);
        when(serverService.getRequired(3L)).thenReturn(server);
        when(clientFactory.create(server)).thenReturn(client);
        when(client.callTool(any())).thenReturn(new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("done")), false));
        ToolDefinition definition = definition();

        ToolExecutionResult result = new McpToolExecutor(clientFactory, serverService, new ObjectMapper())
                .execute(definition, new ToolExecutionContext("req-5", Map.of()));

        assertThat(result.data()).isEqualTo("done");
    }

    private ToolDefinition definition() {
        ToolDefinition definition = new ToolDefinition();
        definition.setToolCode("weather");
        definition.setToolType(ToolType.MCP);
        definition.setAvailability(ToolAvailability.AVAILABLE);
        definition.setMcpServerId(3L);
        definition.setRemoteToolName("weather_query");
        return definition;
    }
}
