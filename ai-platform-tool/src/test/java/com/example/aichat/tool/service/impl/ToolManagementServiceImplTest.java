package com.example.aichat.tool.service.impl;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.dto.management.ToolUpsertRequest;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolHttpMethod;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.validation.ToolConfigCodec;
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
class ToolManagementServiceImplTest {
    @Mock
    private ToolDefinitionMapper mapper;
    @Mock
    private UrlSafetyValidator urlSafetyValidator;

    private ToolManagementServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ToolProperties properties = new ToolProperties();
        ToolCredentialHeaders credentialHeaders = new ToolCredentialHeaders(
                new AiCredentialCipher(java.util.Base64.getEncoder().encodeToString(new byte[32])), objectMapper);
        service = new ToolManagementServiceImpl(
                mapper,
                new ToolConfigCodec(objectMapper),
                credentialHeaders,
                urlSafetyValidator,
                properties,
                objectMapper);
    }

    @Test
    void createsHttpToolWithEncryptedCredentials() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(urlSafetyValidator.validate("https://example.com/orders")).thenReturn(URI.create("https://example.com/orders"));
        when(mapper.insert(any(ToolDefinition.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ToolDefinition.class).setId(7L);
            return 1;
        });

        var result = service.create(request("secret-token"));

        assertThat(result.id()).isEqualTo(7L);
        assertThat(result.toolCode()).isEqualTo("orders");
        assertThat(result.hasCredential()).isTrue();
        assertThat(result.authType()).isEqualTo(ToolAuthType.BEARER);
        assertThat(result.authHeaderName()).isNull();
        assertThat(result.httpConfig().get("url").asText()).isEqualTo("https://example.com/orders");
        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCredentialCiphertext()).doesNotContain("secret-token");
        assertThat(captor.getValue().getStatus()).isZero();
    }

    @Test
    void exposesApiKeyAuthenticationConfiguration() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(urlSafetyValidator.validate("https://example.com/orders"))
                .thenReturn(URI.create("https://example.com/orders"));
        when(mapper.insert(any(ToolDefinition.class))).thenAnswer(invocation -> 1);

        var result = service.create(request(ToolAuthType.API_KEY, "X-Api-Key", "secret-token"));

        assertThat(result.authType()).isEqualTo(ToolAuthType.API_KEY);
        assertThat(result.authHeaderName()).isEqualTo("X-Api-Key");
    }

    @Test
    void requiresCredentialForAuthenticatedToolCreation() {
        when(mapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    }

    @Test
    void rejectsManualMcpToolConfiguration() {
        ToolUpsertRequest request = new ToolUpsertRequest(
                "remote", "Remote", null, ToolType.MCP, null, ToolAuthType.NONE,
                null, null, null, 0);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    }

    private ToolUpsertRequest request(String credential) {
        return request(ToolAuthType.BEARER, null, credential);
    }

    private ToolUpsertRequest request(ToolAuthType authType, String authHeaderName, String credential) {
        return new ToolUpsertRequest(
                " orders ", "Orders", "Order lookup", ToolType.HTTP,
                new HttpToolConfig("https://example.com/orders", ToolHttpMethod.POST,
                        Map.of("Accept", "application/json"), Map.of(),
                        new ObjectMapper().createObjectNode().put("id", "{{orderId}}")),
                authType, authHeaderName, credential, Map.of("X-Tenant", "demo"), null);
    }
}
