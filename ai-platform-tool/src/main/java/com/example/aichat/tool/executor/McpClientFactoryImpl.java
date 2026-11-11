package com.example.aichat.tool.executor;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Map;

@Service
public class McpClientFactoryImpl implements McpClientFactory {
    private final ToolCredentialHeaders credentialHeaders;
    private final UrlSafetyValidator urlSafetyValidator;
    private final JsonSchemaValidator jsonSchemaValidator;
    private final ToolProperties properties;

    public McpClientFactoryImpl(ToolCredentialHeaders credentialHeaders,
                                UrlSafetyValidator urlSafetyValidator,
                                JsonSchemaValidator jsonSchemaValidator,
                                ToolProperties properties) {
        this.credentialHeaders = credentialHeaders;
        this.urlSafetyValidator = urlSafetyValidator;
        this.jsonSchemaValidator = jsonSchemaValidator;
        this.properties = properties;
    }

    @Override
    public McpSyncClient create(ToolMcpServer server) {
        URI endpoint = urlSafetyValidator.validate(server.getEndpointUrl());
        Map<String, String> headers = credentialHeaders.resolve(server.getAuthType(), server.getAuthHeaderName(),
                server.getCredentialCiphertext(), server.getSecretHeadersCiphertext());
        HttpClientStreamableHttpTransport.Builder transportBuilder = HttpClientStreamableHttpTransport
                .builder(endpoint.toString())
                .connectTimeout(Duration.ofSeconds(timeout(server.getConnectTimeout(), properties.getDefaultConnectTimeout())))
                .customizeRequest(request -> headers.forEach(request::header));
        return McpClient.sync(transportBuilder.build())
                .requestTimeout(Duration.ofSeconds(timeout(server.getReadTimeout(), properties.getDefaultReadTimeout())))
                .initializationTimeout(Duration.ofSeconds(timeout(server.getReadTimeout(), properties.getDefaultReadTimeout())))
                .jsonSchemaValidator(jsonSchemaValidator)
                .clientInfo(new io.modelcontextprotocol.spec.McpSchema.Implementation("AI-Chat Tool Client", "1.0"))
                .build();
    }

    private int timeout(Integer value, int fallback) {
        int timeout = value == null ? fallback : value;
        if (timeout < 1 || timeout > properties.getMaxTimeout()) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "MCP timeout is invalid");
        }
        return timeout;
    }
}
