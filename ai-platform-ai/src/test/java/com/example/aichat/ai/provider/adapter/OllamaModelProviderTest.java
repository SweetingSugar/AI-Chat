package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

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
                    10,
                    2L,
                    "llama3.2",
                    8192,
                    1024,
                    BigDecimal.valueOf(0.2),
                    BigDecimal.valueOf(0.3),
                    1
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
    void treatsReadTimeoutAsSeconds() throws IOException {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String response = "{\"model\":\"llama3.2\",\"message\":{\"role\":\"assistant\",\"content\":\"hello\"},\"done\":true}\n";
        server.createContext("/api/chat", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
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
                    1,
                    2L,
                    "llama3.2",
                    8192,
                    1024,
                    null,
                    null,
                    1
            );

            List<AiStreamEvent> events = new OllamaModelProvider()
                    .stream(snapshot, List.of(new AiMessageDTO(AiMessageRole.USER, "hello")), null)
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertEquals(1, requestBodies.size());
            assertEquals("hello", events.stream().map(event -> event.delta() == null ? "" : event.delta()).reduce("", String::concat), events.toString());
            assertTrue(events.stream().noneMatch(event -> event.type() == com.example.aichat.ai.stream.AiStreamEventType.ERROR));
        } finally {
            server.stop(0);
        }
    }
}
