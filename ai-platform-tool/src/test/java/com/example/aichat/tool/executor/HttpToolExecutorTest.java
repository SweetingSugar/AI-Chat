package com.example.aichat.tool.executor;

import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolHttpMethod;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.TemplateValueResolver;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpToolExecutorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UrlSafetyValidator urlSafetyValidator = mock(UrlSafetyValidator.class);
    private HttpServer server;
    private String endpoint;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/orders";
        when(urlSafetyValidator.validate(anyString())).thenAnswer(invocation -> URI.create(invocation.getArgument(0)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void executesTemplatedJsonRequestAndParsesJsonResponse() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> tenant = new AtomicReference<>();
        server.createContext("/orders", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            tenant.set(exchange.getRequestHeaders().getFirst("X-Tenant"));
            respond(exchange, 200, "{\"ok\":true,\"orderId\":\"A-1\"}", "application/json");
        });
        ToolProperties properties = new ToolProperties();
        HttpToolExecutor executor = executor(properties);
        ToolDefinition definition = tool(new HttpToolConfig(
                endpoint, ToolHttpMethod.POST, Map.of("X-Tenant", "{{tenant}}"),
                Map.of("region", "{{region}}"), objectMapper.createObjectNode().put("orderId", "{{orderId}}")));

        ToolExecutionResult result = executor.execute(definition,
                new ToolExecutionContext("req-1", Map.of("tenant", "demo", "region", "cn", "orderId", "A-1")));

        assertThat(result.success()).isTrue();
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.data()).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) result.data()).get("ok").asBoolean()).isTrue();
        assertThat(requestBody).hasValue("{\"orderId\":\"A-1\"}");
        assertThat(tenant).hasValue("demo");
    }

    @Test
    void rejectsResponsesThatExceedConfiguredLimit() throws Exception {
        server.createContext("/orders", exchange -> respond(exchange, 200, "123456789", "text/plain"));
        ToolProperties properties = new ToolProperties();
        properties.setMaxResponseBytes(4);
        HttpToolExecutor executor = executor(properties);

        assertThatThrownBy(() -> executor.execute(tool(new HttpToolConfig(
                        endpoint, ToolHttpMethod.GET, Map.of(), Map.of(), null)),
                new ToolExecutionContext("req-2", Map.of())))
                .isInstanceOf(ToolRemoteException.class)
                .hasMessageContaining("response");
    }

    @Test
    void doesNotAllowArgumentsToReplaceConfiguredAuthenticationHeader() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server.createContext("/orders", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}", "application/json");
        });
        ObjectMapper mapper = new ObjectMapper();
        ToolCredentialHeaders credentialHeaders = new ToolCredentialHeaders(
                new com.example.aichat.ai.config.AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])), mapper);
        String ciphertext = credentialHeaders.encryptCredential("server-token");
        HttpToolExecutor executor = new HttpToolExecutor(
                RestClient.builder(), new ToolProperties(), new ToolConfigCodec(mapper),
                new TemplateValueResolver(mapper), credentialHeaders, urlSafetyValidator, mapper);
        ToolDefinition definition = tool(new HttpToolConfig(endpoint, ToolHttpMethod.GET,
                Map.of(), Map.of(), null));
        definition.setAuthType(ToolAuthType.BEARER);
        definition.setCredentialCiphertext(ciphertext);

        executor.execute(definition, new ToolExecutionContext("req-3", Map.of("Authorization", "attacker")));

        assertThat(authorization).hasValue("Bearer server-token");
    }

    private HttpToolExecutor executor(ToolProperties properties) {
        ToolCredentialHeaders credentials = new ToolCredentialHeaders(
                new com.example.aichat.ai.config.AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])), objectMapper);
        return new HttpToolExecutor(RestClient.builder(), properties, new ToolConfigCodec(objectMapper),
                new TemplateValueResolver(objectMapper), credentials, urlSafetyValidator, objectMapper);
    }

    private ToolDefinition tool(HttpToolConfig config) {
        ToolDefinition definition = new ToolDefinition();
        definition.setId(1L);
        definition.setToolCode("orders");
        definition.setToolType(ToolType.HTTP);
        definition.setHttpConfig(new ToolConfigCodec(objectMapper).encode(config));
        definition.setAuthType(ToolAuthType.NONE);
        return definition;
    }

    private void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
