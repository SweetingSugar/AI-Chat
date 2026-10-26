# Ollama Model Provider Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add native LangChain4j Ollama streaming support to the AI module while preserving the existing model routing, SSE events, usage recording, and provider management flows.

**Architecture:** Add an `OllamaModelProvider` that implements the existing `ModelProvider` interface and delegates streaming to `OllamaStreamingChatModel`. Register it through the existing `AiProviderType`/`AiModelFactory` path. Make provider credentials optional only for Ollama and keep the existing encrypted credential behavior for all other provider types.

**Tech Stack:** Java 17, Spring Boot 3.5.16, LangChain4j 1.21.0, Reactor, MyBatis-Plus, Flyway, JUnit 5, Mockito, JDK `HttpServer` for provider adapter tests.

---

## File map

### Files to create

- `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java` — native Ollama streaming adapter.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/AiModelFactoryTest.java` — verifies that an Ollama provider is selected for an Ollama snapshot.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java` — verifies native streaming, endpoint/model selection, and generation options through a local JDK HTTP server.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiProviderServiceImplTest.java` — verifies Ollama credential rules and existing-provider credential preservation.
- `ai-platform-ai/src/main/resources/db/migration/V6__ollama_provider_credentials.sql` — makes the provider credential column nullable. `V6` follows the existing project-wide migration sequence through `V5`.

### Files to modify

- `ai-platform-ai/pom.xml` — add the LangChain4j Ollama integration module.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/enums/AiProviderType.java` — add `OLLAMA`.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiProviderServiceImpl.java` — allow empty credentials only for Ollama and validate provider-type changes correctly.
- `docs/backend-development-guide.md` — document the fourth provider type and native Ollama configuration.
- `docs/frontend-api-integration.md` — keep the provider enum and `apiKey` rules accurate for admin clients.

### Files deliberately unchanged

- `AiModelConfigSnapshot`, `AiModelRouter`, `AiModelFactory`, `ModelProvider`, `LangChain4jStreamAdapter`, and `DefaultAiChatService` already provide the required extension points.
- `V2__ai_provider_model_schema.sql` is historical and must not be edited; the nullable-column change belongs in a new migration.

---

### Task 1: Add failing provider routing and native streaming tests

**Files:**
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/AiModelFactoryTest.java`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java`

- [ ] **Step 1: Write the failing factory routing test**

Create `AiModelFactoryTest.java` with this complete test:

```java
package com.example.aichat.ai.provider;

import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.adapter.OllamaModelProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class AiModelFactoryTest {
    @Test
    void routesOllamaSnapshotToOllamaProvider() {
        OllamaModelProvider ollamaProvider = new OllamaModelProvider();
        AiModelFactory factory = new AiModelFactory(List.of(ollamaProvider));
        AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                1L,
                "Ollama",
                AiProviderType.OLLAMA,
                "http://localhost:11434",
                null,
                10,
                60,
                2L,
                "llama3.2",
                8192,
                1024,
                BigDecimal.valueOf(0.7),
                BigDecimal.valueOf(0.9),
                1
        );

        assertSame(ollamaProvider, factory.get(snapshot));
    }
}
```

- [ ] **Step 2: Write the failing native streaming test**

Create `OllamaModelProviderTest.java` with a local JDK HTTP server. The server responds with the NDJSON shape returned by Ollama, captures the request body, and the test verifies the model and request-level generation options:

```java
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

            assertEquals("hello world", events.stream().map(AiStreamEvent::delta).reduce("", String::concat));
            assertEquals(1, requestBodies.size());
            assertTrue(requestBodies.get(0).contains("\"model\":\"llama3.2\""));
            assertTrue(requestBodies.get(0).contains("\"temperature\":0.7"));
            assertTrue(requestBodies.get(0).contains("\"top_p\":0.8"));
        } finally {
            server.stop(0);
        }
    }
}
```

- [ ] **Step 3: Run the new tests and verify they fail for the missing provider**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiModelFactoryTest,OllamaModelProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation fails because `AiProviderType.OLLAMA` and `OllamaModelProvider` do not exist yet. No production implementation should be added before observing this failure.

---

### Task 2: Implement and register the native Ollama provider

**Files:**
- Modify: `ai-platform-ai/pom.xml:21-24`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/enums/AiProviderType.java:3`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java`

- [ ] **Step 1: Add the LangChain4j Ollama dependency**

Add this dependency immediately after `langchain4j-open-ai` in `ai-platform-ai/pom.xml`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-ollama</artifactId>
</dependency>
```

The root BOM already manages version `1.21.0`; do not add a second version property.

- [ ] **Step 2: Register the provider type**

Replace the one-line enum declaration with:

```java
public enum AiProviderType {OPENAI, DEEPSEEK, QWEN, OLLAMA}
```

- [ ] **Step 3: Implement the minimal adapter**

Create `OllamaModelProvider.java` with this implementation:

```java
package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.context.MessageConverter;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.ModelProvider;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.stream.LangChain4jStreamAdapter;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaModelProvider implements ModelProvider {
    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.OLLAMA;
    }

    @Override
    public Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages,
                                      AiGenerationOptions options) {
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
                return LangChain4jStreamAdapter.adapt(
                        builder.build().chat(MessageConverter.toLangChain4j(messages))
                );
            } catch (RuntimeException error) {
                return Flux.just(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
            }
        });
    }
}
```

The adapter deliberately has no credential resolver dependency because native Ollama does not use an API key. The existing `LangChain4jStreamAdapter` handles `PartialResponse` events and downstream cancellation exactly as it does for OpenAI-compatible providers.

- [ ] **Step 4: Run the provider tests and compile the AI module**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiModelFactoryTest,OllamaModelProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both new tests pass. Then run:

```bash
mvn -pl ai-platform-ai -am -DskipTests compile
```

Expected: the AI module and its dependencies compile without changes to the existing routing or streaming classes.

- [ ] **Step 5: Commit the provider implementation**

```bash
git add ai-platform-ai/pom.xml ai-platform-ai/src/main/java/com/example/aichat/ai/enums/AiProviderType.java ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java ai-platform-ai/src/test/java/com/example/aichat/ai/provider/AiModelFactoryTest.java ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java
git commit -m "Add native Ollama model provider"
```

---

### Task 3: Add failing tests for optional Ollama credentials

**Files:**
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiProviderServiceImplTest.java`

- [ ] **Step 1: Write the credential behavior tests**

Create `AiProviderServiceImplTest.java` with this complete test class:

```java
package com.example.aichat.ai.service.impl;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.mapper.AiProviderMapper;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderServiceImplTest {
    @Mock
    private AiProviderMapper providerMapper;
    @Mock
    private AiModelMapper modelMapper;

    private AiProviderServiceImpl service;
    private AiCredentialCipher credentialCipher;

    @BeforeEach
    void setUp() {
        credentialCipher = new AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new AiProviderServiceImpl(providerMapper, modelMapper, credentialCipher);
        when(providerMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void createsOllamaProviderWithoutApiKey() {
        AiProviderUpsertRequest request = request(AiProviderType.OLLAMA, null);

        assertDoesNotThrow(() -> service.create(request));
        verify(providerMapper).insert(argThat(provider -> provider.getApiKeyCiphertext() == null));
    }

    @Test
    void rejectsMissingApiKeyForNonOllamaProvider() {
        AiProviderUpsertRequest request = request(AiProviderType.OPENAI, null);

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void updateWithoutApiKeyPreservesExistingCredential() {
        String encryptedCredential = credentialCipher.encrypt("existing-key");
        AiProvider provider = new AiProvider();
        provider.setId(7L);
        provider.setProviderCode("ollama");
        provider.setProviderName("Ollama");
        provider.setProviderType(AiProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        provider.setApiKeyCiphertext(encryptedCredential);
        provider.setCredentialVersion(1);
        when(providerMapper.selectById(7L)).thenReturn(provider);
        when(providerMapper.updateById(any())).thenReturn(1);

        service.update(7L, request(AiProviderType.OLLAMA, null));

        assertEquals(encryptedCredential, provider.getApiKeyCiphertext());
    }

    @Test
    void rejectsChangingToNonOllamaWithoutExistingOrNewCredential() {
        AiProvider provider = new AiProvider();
        provider.setId(8L);
        provider.setProviderCode("ollama");
        provider.setProviderName("Ollama");
        provider.setProviderType(AiProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        provider.setApiKeyCiphertext(null);
        provider.setCredentialVersion(1);
        when(providerMapper.selectById(8L)).thenReturn(provider);

        assertThrows(BusinessException.class, () -> service.update(8L, request(AiProviderType.OPENAI, null)));
    }

    private AiProviderUpsertRequest request(AiProviderType providerType, String apiKey) {
        return new AiProviderUpsertRequest(
                "provider-code",
                "Provider",
                providerType,
                "http://localhost:11434",
                apiKey,
                10,
                60,
                1,
                0,
                null
        );
    }
}
```

Remove the unused `assertNull` import if the compiler flags it; the test assertions otherwise must remain unchanged.

- [ ] **Step 2: Run the service tests and observe the expected failure**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiProviderServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `createsOllamaProviderWithoutApiKey` and `rejectsChangingToNonOllamaWithoutExistingOrNewCredential` fail because the current service unconditionally requires a new API key only during creation and permits the invalid provider-type transition during update.

---

### Task 4: Implement provider credential rules

**Files:**
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiProviderServiceImpl.java:54-76`

- [ ] **Step 1: Add the provider-type-aware credential predicate**

Add this private method before `assertCodeAvailable`:

```java
private boolean requiresCredential(AiProviderType providerType) {
    return providerType != AiProviderType.OLLAMA;
}
```

Add the missing import at the top of the file:

```java
import com.example.aichat.ai.enums.AiProviderType;
```

- [ ] **Step 2: Update provider creation validation and storage**

Replace the current `create` method body with this version:

```java
@Override
@Transactional
public AiProviderManagementDTO create(AiProviderUpsertRequest request) {
    assertCodeAvailable(request.providerCode(), null);
    if (requiresCredential(request.providerType()) && !StringUtils.hasText(request.apiKey())) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required when creating a provider");
    }
    AiProvider provider = copy(request, new AiProvider());
    provider.setApiKeyCiphertext(StringUtils.hasText(request.apiKey())
            ? credentialCipher.encrypt(request.apiKey().trim())
            : null);
    provider.setCredentialVersion(1);
    mapper.insert(provider);
    return toManagementDto(provider);
}
```

This preserves the existing encrypted storage path when an optional Ollama API key is supplied while allowing the normal native configuration to remain null.

- [ ] **Step 3: Validate updates when a provider changes type**

Replace the current `update` method with this version:

```java
@Override
@Transactional
public AiProviderManagementDTO update(Long providerId, AiProviderUpsertRequest request) {
    AiProvider provider = getRequired(providerId);
    assertCodeAvailable(request.providerCode(), providerId);
    if (requiresCredential(request.providerType())
            && !StringUtils.hasText(request.apiKey())
            && !StringUtils.hasText(provider.getApiKeyCiphertext())) {
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required for this provider type");
    }
    copy(request, provider);
    if (StringUtils.hasText(request.apiKey())) {
        provider.setApiKeyCiphertext(credentialCipher.encrypt(request.apiKey().trim()));
        provider.setCredentialVersion(provider.getCredentialVersion() == null ? 1 : provider.getCredentialVersion() + 1);
    }
    mapper.updateById(provider);
    return toManagementDto(provider);
}
```

Do not clear an existing credential when an update omits `apiKey`; this keeps the existing API contract. The Ollama adapter never resolves this field.

- [ ] **Step 4: Run the service tests**

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiProviderServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all four tests pass.

- [ ] **Step 5: Commit credential handling**

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiProviderServiceImpl.java ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiProviderServiceImplTest.java
git commit -m "Allow credentialless Ollama providers"
```

---

### Task 5: Add the database migration and update API documentation

**Files:**
- Create: `ai-platform-ai/src/main/resources/db/migration/V6__ollama_provider_credentials.sql`
- Modify: `docs/backend-development-guide.md:69-74`
- Modify: `docs/frontend-api-integration.md:386-395`

- [ ] **Step 1: Add the nullable credential migration**

Create `V6__ollama_provider_credentials.sql` with exactly:

```sql
alter table ai_provider
    modify column api_key_ciphertext text null;
```

Do not modify `V2__ai_provider_model_schema.sql`; Flyway must apply this change to existing installations as `V6` after the current `V1`–`V5` migrations.

- [ ] **Step 2: Document the native Ollama provider in the backend guide**

In the provider bullet currently listing three provider types, replace it with:

```markdown
- 已注册四种供应商类型：`OPENAI`、`DEEPSEEK`、`QWEN`、`OLLAMA`。前三者通过 OpenAI 兼容的 LangChain4j 流式模型适配器发起调用；`OLLAMA` 使用 LangChain4j 原生 Ollama 流式适配器。
- Ollama 供应商通常将 `baseUrl` 配置为 `http://localhost:11434`，模型代码填写 Ollama 中已拉取的模型名；原生 Ollama 不要求 API Key。
```

Keep the surrounding routing and encryption documentation unchanged.

- [ ] **Step 3: Keep the frontend API contract accurate**

Make these two targeted documentation changes in `docs/frontend-api-integration.md`:

Replace the provider type row with:

```markdown
| `providerType` | 是 | `OPENAI`、`DEEPSEEK`、`QWEN`、`OLLAMA` |
```

Replace the API key row with:

```markdown
| `apiKey` | 非 Ollama 供应商新增时是；Ollama 可省略 | 最长 4096；更新时为空或缺省则保留已有 API Key |
```

Add the following sentence after the field table:

```markdown
`OLLAMA` 供应商通常使用 `http://localhost:11434` 作为 `baseUrl`，`apiKey` 可以省略。
```

- [ ] **Step 4: Inspect the migration and documentation diff**

Run:

```bash
git diff --check
git diff -- ai-platform-ai/src/main/resources/db/migration/V6__ollama_provider_credentials.sql docs/backend-development-guide.md docs/frontend-api-integration.md
```

Expected: no whitespace errors, one new SQL migration, and only the provider type/API key documentation changes described above.

- [ ] **Step 5: Commit migration and documentation**

```bash
git add ai-platform-ai/src/main/resources/db/migration/V6__ollama_provider_credentials.sql docs/backend-development-guide.md docs/frontend-api-integration.md
git commit -m "Document Ollama provider configuration"
```

---

### Task 6: Run the complete verification suite

**Files:**
- No source changes expected unless a verification command identifies a concrete failure.

- [ ] **Step 1: Run all AI-module tests**

```bash
mvn -pl ai-platform-ai -am test
```

Expected: the existing module tests and the new factory, Ollama adapter, and credential tests all pass.

- [ ] **Step 2: Package all modules without skipping compilation**

```bash
mvn clean package -DskipTests
```

Expected: all six Maven modules package successfully. The command may remove generated `target` output and recreate it; do not commit generated build artifacts.

- [ ] **Step 3: Verify the final diff and working tree**

```bash
git diff --check
git status --short
git log --oneline -5
```

Expected: no whitespace errors; only the pre-existing untracked `.DS_Store` may remain, and no `target/` files or credentials are staged.

- [ ] **Step 4: Perform the deployment-level Ollama smoke check when an Ollama service is available**

Configure an enabled provider through the existing admin endpoint with:

```json
{
  "providerCode": "ollama-local",
  "providerName": "Ollama Local",
  "providerType": "OLLAMA",
  "baseUrl": "http://localhost:11434",
  "connectTimeout": 10,
  "readTimeout": 60,
  "status": 1,
  "sort": 0
}
```

Create an enabled model whose `modelCode` exactly matches a locally pulled Ollama model, set it as the default, and send one existing chat stream request. Verify that the client receives the existing `start`, `delta`, and `complete` SSE events. If no Ollama service is available, report this smoke check as not run; the local HTTP-server unit test remains the automated provider verification.

## Self-review checklist

- **Spec coverage:** native adapter, `OLLAMA` enum, LangChain4j dependency, nullable credential migration, provider validation, existing routing/stream reuse, error mapping, tests, backend documentation, frontend contract documentation, and smoke-check boundaries are all covered.
- **Placeholder scan:** the plan contains no `TBD`, `TODO`, or unspecified implementation steps; every production code change has an exact file and code block.
- **Type consistency:** `AiProviderType.OLLAMA`, `OllamaModelProvider`, `OllamaStreamingChatModel.builder()`, `AiModelConfigSnapshot`, and `AiProviderServiceImpl` signatures are consistent across tasks.
- **Scope:** no chat API, SSE format, usage recorder, or unrelated provider refactor is included.
