package com.example.aichat.tool.service.impl;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolQueryServiceImplTest {
    @Mock
    private ToolDefinitionMapper mapper;
    @Mock
    private McpServerManagementService serverService;

    private ToolQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ToolQueryServiceImpl(mapper, serverService, new ObjectMapper());
    }

    @Test
    void listsOnlyEnabledAvailableToolsWithEnabledMcpServersInIdOrder() {
        ToolDefinition http = tool(2L, "http_tool", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null,
                "{\"type\":\"object\"}");
        ToolDefinition mcp = tool(4L, "mcp_tool", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 8L,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        ToolDefinition disabled = tool(5L, "disabled", ToolType.HTTP, 0, ToolAvailability.AVAILABLE, null, null);
        ToolDefinition stale = tool(6L, "stale", ToolType.HTTP, 1, ToolAvailability.STALE, null, null);
        ToolDefinition deleted = tool(7L, "deleted", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null, null);
        deleted.setDeleted(1);
        ToolDefinition noServerId = tool(8L, "no_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, null, null);
        ToolDefinition missingServer = tool(9L, "missing_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 9L, null);
        ToolDefinition disabledServer = tool(10L, "disabled_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 10L, null);

        when(mapper.selectList(any())).thenReturn(List.of(
                mcp, disabledServer, stale, http, deleted, noServerId, missingServer, disabled));
        when(serverService.getRequired(8L)).thenReturn(server(8L, 1));
        when(serverService.getRequired(9L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MCP server not found"));
        when(serverService.getRequired(10L)).thenReturn(server(10L, 0));

        List<AvailableToolView> result = service.listAvailable();

        assertThat(result).extracting(AvailableToolView::toolCode)
                .containsExactly("http_tool", "mcp_tool");
        assertThat(result.get(0).inputSchema().path("type").asText()).isEqualTo("object");
        assertThat(result.get(1).inputSchema().path("properties").path("city").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void rejectsInvalidStoredInputSchemaWithToolConfigError() {
        when(mapper.selectList(any())).thenReturn(List.of(
                tool(1L, "broken", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null, "not-json")));

        assertThatThrownBy(() -> service.listAvailable())
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.TOOL_CONFIG_INVALID);
    }

    @Test
    void responseModelContainsNoManagementOrCredentialFields() throws Exception {
        when(mapper.selectList(any())).thenReturn(List.of(
                tool(1L, "safe", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null,
                        "{\"type\":\"object\"}")));

        String json = new ObjectMapper().writeValueAsString(service.listAvailable().get(0));

        assertThat(json).contains("toolCode", "toolName", "description", "toolType", "inputSchema")
                .doesNotContain("httpConfig", "authType", "authHeaderName", "hasCredential",
                        "credentialVersion", "mcpServerId", "status", "availability", "credential");
    }

    private ToolDefinition tool(Long id, String code, ToolType type, int status,
                                ToolAvailability availability, Long serverId, String schema) {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(id);
        tool.setToolCode(code);
        tool.setToolName(code + " name");
        tool.setDescription(code + " description");
        tool.setToolType(type);
        tool.setStatus(status);
        tool.setAvailability(availability);
        tool.setMcpServerId(serverId);
        tool.setInputSchema(schema);
        tool.setDeleted(0);
        return tool;
    }

    private ToolMcpServer server(Long id, int status) {
        ToolMcpServer server = new ToolMcpServer();
        server.setId(id);
        server.setStatus(status);
        return server;
    }
}
