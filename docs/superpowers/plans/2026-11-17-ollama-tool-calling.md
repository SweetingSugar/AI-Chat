# Ollama 原生 Tool Calling 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Ollama 原生支持聊天工具定义、工具调用响应和多轮工具执行，同时保持无工具聊天行为不变。

**Architecture:** `OllamaModelProvider` 继续负责创建 `OllamaStreamingChatModel` 和普通流式请求；当请求包含 `AiToolContext` 时，将同一个通用 `StreamingChatModel` 交给现有 `OpenAiToolCallingLoop`。LangChain4j 1.21.0 负责把工具 Schema、assistant tool call 和 tool result 转换为 Ollama `/api/chat` 原生协议，项目继续复用现有权限、执行、大小限制和循环上限。

**Tech Stack:** Java 17, Spring Boot, Reactor, LangChain4j 1.21.0, Ollama `/api/chat` NDJSON, JUnit 5, JDK `HttpServer`。

---

## 文件结构与职责

- **Modify:** `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java`
  - 注入 `AiProperties` 与 `ObjectMapper`；
  - 保留现有 Ollama 模型参数构建；
  - 将带工具请求交给 `OpenAiToolCallingLoop`；
  - 保留无工具请求交给 `LangChain4jStreamAdapter`。
- **Modify:** `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java`
  - 用本地 HTTP server 模拟原生 Ollama tool call 的两轮 NDJSON 响应；
  - 验证工具 Schema、assistant tool call、tool result、最终文本和参数转换；
  - 验证 Ollama 错误返回映射；
  - 保留现有普通聊天和图片测试作为回归。
- **Create:** 无新增生产文件或依赖；`OpenAiToolCallingLoop` 已经依赖通用 `StreamingChatModel`，不需要复制或重写循环。

## Task 1: 添加 Ollama 原生工具调用失败测试

**Files:**
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java`

- [ ] **Step 1: 添加必要的测试 import 和测试方法。**

在现有 import 区域增加：

```java
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.ai.tool.AiToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
```

在现有两个测试后增加以下测试。测试使用真实的 `OllamaModelProvider`、LangChain4j Ollama 适配器和本地 HTTP server；工具执行器只记录调用参数并返回 JSON 数据。

```java
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
        assertTrue(requestBodies.get(0).contains("\"tools\""));
        assertTrue(requestBodies.get(0).contains("\"weather\""));
        assertTrue(requestBodies.get(1).contains("\"tool_calls\""));
        assertTrue(requestBodies.get(1).contains("\"role\":\"tool\""));
        assertEquals("Shanghai is sunny", events.stream()
                .map(event -> event.delta() == null ? "" : event.delta())
                .reduce("", String::concat));
    } finally {
        server.stop(0);
    }
}
```

- [ ] **Step 2: 添加 Ollama provider 错误响应测试。**

在同一测试类增加以下方法，确认原生协议错误不再返回 `AI_TOOL_UNSUPPORTED`，而是进入现有 provider 错误路径：

```java
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
```

- [ ] **Step 3: Run only the new tests to verify they fail for the old implementation.**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest test
```

Expected: `usesNativeToolCallingAndContinuesWithFinalResponse` fails because the current provider emits `AI_TOOL_UNSUPPORTED` without making a request. The existing no-tool and image tests should remain passing. The error test may pass before implementation because the old unsupported path still emits a different code; the native success test is the required red test.

## Task 2: Enable the native Ollama tool path

**Files:**
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java:3-63`

- [ ] **Step 1: Add dependencies and constructors for tool-loop configuration.**

Add these imports:

```java
import com.example.aichat.ai.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
```

Add fields and constructors inside `OllamaModelProvider`:

```java
private final AiProperties properties;
private final ObjectMapper objectMapper;

public OllamaModelProvider() {
    this(new AiProperties(), new ObjectMapper());
}

@Autowired
public OllamaModelProvider(AiProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
}
```

The no-argument constructor preserves the existing direct-instantiation tests. Spring uses the constructor with the shared validated `AiProperties` and application `ObjectMapper`.

- [ ] **Step 2: Replace the unsupported branch with the generic tool loop.**

Remove the `ErrorCode` import and replace the current four-argument `stream` method with:

```java
@Override
public Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages,
                                  AiGenerationOptions options, AiToolContext toolContext) {
    return Flux.defer(() -> {
        try {
            var builder = OllamaStreamingChatModel.builder()
                    .baseUrl(snapshot.baseUrl())
                    .modelName(snapshot.modelCode())
                    .timeout(Duration.ofMillis(snapshot.readTimeout() == null ? 120000 : snapshot.readTimeout()));
            if (options != null && options.temperature() != null) {
                builder.temperature(options.temperature());
            } else if (snapshot.temperature() != null) {
                builder.temperature(snapshot.temperature().doubleValue());
            }
            if (options != null && options.topP() != null) {
                builder.topP(options.topP());
            } else if (snapshot.topP() != null) {
                builder.topP(snapshot.topP().doubleValue());
            }

            var model = builder.build();
            List<dev.langchain4j.data.message.ChatMessage> converted =
                    MessageConverter.toLangChain4j(messages);
            if (toolContext != null) {
                return new OpenAiToolCallingLoop(model, toolContext, objectMapper,
                        properties.getMaxToolRounds(), properties.getMaxToolResultBytes())
                        .stream(converted);
            }
            return LangChain4jStreamAdapter.adapt(model, converted);
        } catch (RuntimeException error) {
            return Flux.just(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
        }
    });
}
```

This uses the Ollama model as a `StreamingChatModel`; LangChain4j 1.21.0 will serialize the loop's `ChatRequest.toolSpecifications()` into the native Ollama `tools` field and deserialize `message.tool_calls` into `AiMessage.toolExecutionRequests()`.

- [ ] **Step 3: Run the targeted tests and verify the native loop passes.**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest test
```

Expected: all Ollama provider tests pass, including two HTTP requests in the native tool test, the captured `city` argument, the `tools` field in the first request, the assistant/tool messages in the second request, the final text, and `AI_PROVIDER_ERROR` for the simulated Ollama error.

## Task 3: Verify module behavior and integration boundaries

**Files:**
- Modify: none unless a test exposes a proven regression in the files above.

- [ ] **Step 1: Run the complete AI module tests.**

Run:

```bash
mvn -pl ai-platform-ai -am test
```

Expected: all AI, common, and dependency tests pass. In particular, `OllamaModelProviderTest` no-tool streaming and image requests remain unchanged, while OpenAI-compatible tool-loop tests remain unaffected.

- [ ] **Step 2: Run the tool module tests to verify the execution bridge remains unchanged.**

Run:

```bash
mvn -pl ai-platform-tool -am test
```

Expected: all tool discovery, permission, execution, HTTP, MCP, and AI bridge tests pass. No tool executor or permission code should change.

- [ ] **Step 3: Run the full Maven test suite.**

Run:

```bash
mvn test
```

Expected: exit code 0 with all module tests passing.

- [ ] **Step 4: Compile and verify dependency direction.**

Run:

```bash
mvn -DskipTests compile
mvn -q -pl ai-platform-ai dependency:tree -Dincludes=com.example.aichat:ai-platform-tool
```

Expected: compilation succeeds and the AI module has no dependency on `ai-platform-tool`; the existing dependency direction remains `ai-platform-tool -> ai-platform-ai`.

- [ ] **Step 5: Inspect the final diff and working tree.**

Run:

```bash
git diff --check
 git diff -- ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java
git status --short
```

Expected: only the Ollama provider and its test contain implementation changes; `.DS_Store` remains untracked and is not staged. Confirm no credentials, complete tool arguments, or internal tool messages are emitted through `AiStreamEvent` or persisted chat messages.
