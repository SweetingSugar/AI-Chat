package com.example.aichat.tool.service.impl;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.management.McpServerUpsertRequest;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.mapper.ToolMcpServerMapper;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerManagementServiceImplTest {
    @Mock
    private ToolMcpServerMapper mapper;
    @Mock
    private ToolDefinitionMapper toolMapper;
    @Mock
    private UrlSafetyValidator urlSafetyValidator;

    private McpServerManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolCredentialHeaders credentialHeaders = new ToolCredentialHeaders(
                new AiCredentialCipher(java.util.Base64.getEncoder().encodeToString(new byte[32])), objectMapper);
        service = new McpServerManagementServiceImpl(
                mapper, toolMapper, credentialHeaders, urlSafetyValidator, new ToolProperties());
    }

    @Test
    void createsStreamableHttpServerWithDefaultsAndEncryptedCredentials() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(urlSafetyValidator.validate("https://mcp.example.com/server")).thenReturn(URI.create("https://mcp.example.com/server"));
        when(mapper.insert(any(ToolMcpServer.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ToolMcpServer.class).setId(9L);
            return 1;
        });

        var result = service.create(new McpServerUpsertRequest(
                " weather ", "Weather MCP", "https://mcp.example.com/server", ToolAuthType.API_KEY,
                "X-Api-Key", "mcp-secret", Map.of("X-Tenant", "demo"), null, null, null));

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.serverCode()).isEqualTo("weather");
        assertThat(result.connectTimeout()).isEqualTo(10);
        assertThat(result.readTimeout()).isEqualTo(60);
        assertThat(result.hasCredential()).isTrue();
        ArgumentCaptor<ToolMcpServer> captor = ArgumentCaptor.forClass(ToolMcpServer.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCredentialCiphertext()).doesNotContain("mcp-secret");
    }

    @Test
    void preventsDeletingServerWithDiscoveredTools() {
        ToolMcpServer server = new ToolMcpServer();
        server.setId(9L);
        when(mapper.selectById(9L)).thenReturn(server);
        when(toolMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.RESOURCE_CONFLICT);
    }
}
