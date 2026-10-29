package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
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

class OpenAiModelProviderTest {
    @Test
    void sendsImageAsDataUrlToOpenAiCompatibleEndpoint() throws IOException {
        CopyOnWriteArrayList<String> requestBodies = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = ("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\n"
                + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
        server.createContext("/v1/chat/completions", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        try {
            AiCredentialResolver resolver = ignored -> "key";
            AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                    1L, "OpenAI", AiProviderType.OPENAI,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                    "encrypted", 10, 10, 2L, "vision-model", 8192, 1024,
                    BigDecimal.valueOf(0.2), BigDecimal.valueOf(0.3), 1, true);
            AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "describe",
                    List.of(AiMessageContentDTO.image("image/png", new byte[]{1, 2})));

            List<AiStreamEvent> events = new OpenAiModelProvider(resolver)
                    .stream(snapshot, List.of(message), null)
                    .collectList()
                    .block(Duration.ofSeconds(10));

            assertEquals(1, requestBodies.size());
            String requestBody = requestBodies.get(0).replaceAll("\\s+", "");
            assertTrue(requestBody.contains("data:image/png;base64,AQI="));
            assertTrue(events.stream().anyMatch(event -> "ok".equals(event.delta())));
        } finally {
            server.stop(0);
        }
    }
}
