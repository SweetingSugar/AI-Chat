package com.example.aichat.tool.config;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.tool.executor.HttpToolExecutor;
import com.example.aichat.tool.executor.McpClientFactory;
import com.example.aichat.tool.executor.McpToolExecutor;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.validation.TemplateValueResolver;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.json.schema.jackson2.DefaultJsonSchemaValidator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ToolProperties.class)
public class ToolConfiguration {
    @Bean
    public JsonSchemaValidator toolJsonSchemaValidator(ObjectMapper objectMapper) {
        return new DefaultJsonSchemaValidator(objectMapper);
    }

    @Bean
    public TemplateValueResolver templateValueResolver(ObjectMapper objectMapper) {
        return new TemplateValueResolver(objectMapper);
    }

    @Bean
    public ToolConfigCodec toolConfigCodec(ObjectMapper objectMapper) {
        return new ToolConfigCodec(objectMapper);
    }

    @Bean
    public UrlSafetyValidator urlSafetyValidator(ToolProperties properties) {
        return new UrlSafetyValidator(properties);
    }

    @Bean
    @ConditionalOnBean(AiCredentialCipher.class)
    public ToolCredentialHeaders toolCredentialHeaders(AiCredentialCipher credentialCipher,
                                                       ObjectMapper objectMapper) {
        return new ToolCredentialHeaders(credentialCipher, objectMapper);
    }

    @Bean
    @ConditionalOnBean(RestClient.Builder.class)
    public HttpToolExecutor httpToolExecutor(RestClient.Builder restClientBuilder,
                                              ToolProperties properties,
                                              ToolConfigCodec configCodec,
                                              TemplateValueResolver templateValueResolver,
                                              ToolCredentialHeaders credentialHeaders,
                                              UrlSafetyValidator urlSafetyValidator,
                                              ObjectMapper objectMapper) {
        return new HttpToolExecutor(restClientBuilder, properties, configCodec, templateValueResolver,
                credentialHeaders, urlSafetyValidator, objectMapper);
    }

    @Bean
    @ConditionalOnBean({McpClientFactory.class, McpServerManagementService.class})
    public McpToolExecutor mcpToolExecutor(McpClientFactory clientFactory,
                                            McpServerManagementService serverService,
                                            ObjectMapper objectMapper) {
        return new McpToolExecutor(clientFactory, serverService, objectMapper);
    }

}
