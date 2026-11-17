package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.ai.tool.AiToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaModelProviderTest {
    @Test
    void streamsResponseFromConfiguredOllamaEndpoint() throws IOException {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String response = "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":false}\n"
                + "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\" world\"},\"done\":true}\n";
        server.createContext("/api/chat", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                    1L,
                    "Ollama",
                    AiProviderType.OLLAMA,
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    null,
                    10,
                    10000,
                    2L,
                    "llama3.2",
                    8192,
                    1024,
                    BigDecimal.valueOf(0.2),
                    BigDecimal.valueOf(0.3),
                    1,
                    false
            );
            AiGenerationOptions options = new AiGenerationOptions(null, 0.7, 0.8, null);

            List<AiStreamEvent> events = new OllamaModelProvider()
                    .stream(snapshot, List.of(new AiMessageDTO(AiMessageRole.USER, "hello")), options)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertEquals(1, requestBodies.size());
            assertEquals("hello world", events.stream().map(event -> event.delta() == null ? "" : event.delta()).reduce("", String::concat), events.toString());
            String requestBody = requestBodies.get(0).replaceAll("\\s+", "");
            assertTrue(requestBody.contains("\"model\":\"llama3.2\""));
            assertTrue(requestBody.contains("\"temperature\":0.7"));
            assertTrue(requestBody.contains("\"top_p\":0.8"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sendsImageAsBase64ToOllama() throws IOException {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String response = "{\"model\":\"llava\",\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"done\":true}\n";
        server.createContext("/api/chat", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                    1L, "Ollama", AiProviderType.OLLAMA,
                    "http://127.0.0.1:" + server.getAddress().getPort(), null,
                    10, 10000, 2L, "llava", 8192, 1024,
                    null, null, 1, true);
            AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "describe",
                    List.of(AiMessageContentDTO.image("image/png", new byte[]{1, 2})));

            new OllamaModelProvider().stream(snapshot, List.of(message), null)
                    .collectList().block(Duration.ofSeconds(10));

            assertEquals(1, requestBodies.size());
            assertTrue(requestBodies.get(0).replaceAll("\\s+", "").contains("AQI="));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void usesNativeToolCallingAndContinuesWithFinalResponse() throws IOException {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        AtomicInteger requestNumber = new AtomicInteger();
        AtomicReference<Map<String, Object>> receivedArguments = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String body = requestNumber.getAndIncrement() == 0
                    ? "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\","
                    + "\"tool_calls\":[{\"function\":{\"name\":\"weather\","
                    + "\"arguments\":{\"city\":\"Shanghai\"}}}]},\"done\":true}\n"
                    : "{\"model\":\"qwen3\",\"message\":{\"role\":\"assistant\","
                    + "\"content\":\"Shanghai is sunny\"},\"done\":true}\n";
            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode schema = objectMapper.createObjectNode();
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            properties.putObject("city").put("type", "string");
            schema.putArray("required").add("city");
            AiToolContext toolContext = new AiToolContext(
                    42L,
                    "request-1",
                    List.of(new AiToolDefinition("weather", "Weather", "Get weather", schema)),
                    (toolCode, arguments) -> {
                        receivedArguments.set(arguments);
                        return com.example.aichat.ai.tool.AiToolExecutionResult.success(
                                Map.of("condition", "sunny"));
                    });
            AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                    1L, "Ollama", AiProviderType.OLLAMA,
                    "http://127.0.0.1:" + server.getAddress().getPort(), null,
                    10, 10000, 2L, "qwen3", 8192, 1024,
                    BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.3), 1, false);

            List<AiStreamEvent> events = new OllamaModelProvider()
                    .stream(snapshot, List.of(new AiMessageDTO(AiMessageRole.USER, "What is the weather?")),
                            new AiGenerationOptions(null, 0.7, 0.8, null), toolContext)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertEquals(2, requestBodies.size());
            assertEquals("Shanghai", receivedArguments.get().get("city"));
            String firstRequest = requestBodies.get(0).replaceAll("\\s+", "");
            assertTrue(firstRequest.contains("\"tools\""));
            assertTrue(firstRequest.contains("\"weather\""));
            JsonNode secondRequest = objectMapper.readTree(requestBodies.get(1));
            JsonNode messages = secondRequest.get("messages");
            JsonNode assistantToolCall = messages.get(1).get("tool_calls").get(0);
            assertEquals("assistant", messages.get(1).get("role").asText());
            assertEquals("weather", assistantToolCall.get("function").get("name").asText());
            assertEquals("Shanghai", assistantToolCall.get("function").get("arguments").get("city").asText());
            assertEquals("tool", messages.get(2).get("role").asText());
            assertEquals("{\"condition\":\"sunny\"}", messages.get(2).get("content").asText());
            assertEquals("Shanghai is sunny", events.stream()
                    .map(event -> event.delta() == null ? "" : event.delta())
                    .reduce("", String::concat));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsOllamaToolCallingErrorToProviderError() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":\"model does not support tools\"}\n"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode schema = objectMapper.createObjectNode().put("type", "object");
            AiToolContext toolContext = new AiToolContext(
                    42L, "request-1",
                    List.of(new AiToolDefinition("weather", "Weather", "Get weather", schema)),
                    (toolCode, arguments) -> com.example.aichat.ai.tool.AiToolExecutionResult.success(Map.of()));
            AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                    1L, "Ollama", AiProviderType.OLLAMA,
                    "http://127.0.0.1:" + server.getAddress().getPort(), null,
                    10, 10000, 2L, "qwen3", 8192, 1024,
                    null, null, 1, false);

            List<AiStreamEvent> events = new OllamaModelProvider()
                    .stream(snapshot, List.of(new AiMessageDTO(AiMessageRole.USER, "Use weather")), null, toolContext)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertTrue(events.stream().anyMatch(event -> "AI_PROVIDER_ERROR".equals(event.code())));
        } finally {
            server.stop(0);
        }
    }

}
