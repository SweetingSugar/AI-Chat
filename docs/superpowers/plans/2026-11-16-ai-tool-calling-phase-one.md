# AI Tool Calling Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add explicit per-request HTTP/MCP tool calling to OpenAI-compatible chat providers while preserving the existing no-tool chat, persistence, SSE, and Ollama behavior.

**Architecture:** `ai-platform-ai` owns provider-neutral tool records, a request-scoped tool context, and the model-provider loop. `ai-platform-tool` implements the AI tool SPI, creates executable schemas from persisted HTTP/MCP definitions, and delegates every call to `ToolExecutionService` under the requesting user and `tool:execute` authorization. `ai-platform-chat` only validates, normalizes, and forwards `toolCodes`; tool-call messages remain in memory and only the final model text reaches the existing finalizer.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Reactor, LangChain4j 1.21.0, Jackson, MyBatis-Plus, Sa-Token, JUnit 5, Mockito, existing Testcontainers/Flyway tests.

---

## File Map

### `ai-platform-ai`

- Create `ai/tool/AiToolDefinition.java`: provider-neutral tool name, display metadata, and Jackson JSON Schema.
- Create `ai/tool/AiToolExecutionResult.java`: success/error result returned by a tool executor without exposing tool-module entities.
- Create `ai/tool/AiToolExecutor.java`: functional execution entry point used by the in-memory context.
- Create `ai/tool/AiToolContext.java`: request user, request ID, immutable definitions, and execution entry point.
- Create `ai/tool/AiToolProvider.java`: SPI for preflight preparation; no imports from `ai-platform-tool`.
- Create `provider/adapter/OpenAiToolCallingLoop.java`: LangChain4j request/response loop, ordered multi-call execution, JSON parsing, result limits, and round limits.
- Modify `dto/request/AiChatRequest.java`: carry normalized `toolCodes`.
- Modify `provider/ModelProvider.java`: add a tool-context overload while retaining the old call shape as a no-tool compatibility default.
- Modify `core/DefaultAiChatService.java`: resolve tool context only when codes are present and pass it to the selected provider.
- Modify `provider/adapter/OpenAiModelProvider.java`: keep `LangChain4jStreamAdapter` for no-tool requests and use the new `ChatRequest` loop for tool requests.
- Modify `provider/adapter/OllamaModelProvider.java`: reject non-empty tool context with `AI_TOOL_UNSUPPORTED`, otherwise use the existing path.
- Modify `config/AiProperties.java`, `config/AiConfiguration.java`, and `ai-platform-boot/src/main/resources/application.yml`: add positive `maxToolRounds` and `maxToolResultBytes` settings.
- Modify `ai-platform-common/.../ErrorCode.java`: add `AI_TOOL_UNSUPPORTED`, `AI_TOOL_LOOP_LIMIT`, and `AI_TOOL_UNAVAILABLE`.
- Add focused tests under `ai-platform-ai/src/test/java/com/example/aichat/ai/...` for SPI setup, loop behavior, provider routing, and no-tool regression.

### `ai-platform-tool`

- Create `ai/ToolAiToolProvider.java`: implement the AI SPI, resolve only requested executable definitions, generate schemas, enforce user authorization, and delegate execution.
- Create `ai/HttpToolSchemaGenerator.java`: recursively scan HTTP query/header/body templates and build a JSON Schema without changing execution templates.
- Create `ai/ToolResultSanitizer.java` only if the existing service's safe-message method cannot be reused; otherwise keep sanitization in `ToolExecutionServiceImpl`.
- Modify `service/ToolExecutionService.java` and `service/impl/ToolExecutionServiceImpl.java`: expose a user-scoped internal execution entry point and enforce the same `tool:execute` permission for controller and AI calls.
- Modify `config/ToolConfiguration.java`: register the AI bridge and schema generator.
- Add tests for HTTP schema generation, MCP schema conversion, preflight states, permission propagation, and delegation/audit behavior.

### `ai-platform-chat`

- Modify `dto/ChatRequestDTO.java` and `dto/ChatRegenerateDTO.java`: add up to 20 tool codes with per-code length validation and compatibility constructors.
- Modify `model/ChatGenerationCommand.java`: carry immutable tool codes and preserve existing constructors.
- Modify `service/impl/ChatServiceImpl.java`: trim, reject blank codes, remove duplicates in request order, and pass explicit lists for both normal and regenerate flows.
- Modify `gateway/impl/AiChatGatewayImpl.java`: forward command tool codes into `AiChatRequest`.
- Add gateway/service tests for normal and regenerate propagation, duplicate removal, blank rejection, and no-tool behavior.

### Cross-module verification

- Do not add a Maven dependency from `ai-platform-ai` to `ai-platform-tool`.
- Do not add a migration or chat-message column.
- Do not add a tool-specific SSE event or serialize tool-call messages into chat history.

---

### Task 1: Add the AI tool SPI and request/config contracts

**Files:**
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/tool/AiToolDefinition.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/tool/AiToolExecutionResult.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/tool/AiToolExecutor.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/tool/AiToolContext.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/tool/AiToolProvider.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiChatRequest.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/ModelProvider.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/config/AiProperties.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/config/AiConfiguration.java`
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`
- Modify: `ai-platform-boot/src/main/resources/application.yml`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/tool/AiToolContextTest.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/config/AiConfigurationTest.java`

- [ ] **Step 1: Write failing contract tests.**

  Test that an `AiChatRequest` copies a supplied tool-code list, that `AiToolContext` exposes immutable definitions and delegates execution, and that invalid tool configuration values fail binding or construction. The intended records are:

  ```java
  public record AiToolDefinition(String code, String name, String description,
                                 JsonNode inputSchema) {
      public AiToolDefinition {
          Objects.requireNonNull(code);
          Objects.requireNonNull(name);
          Objects.requireNonNull(inputSchema);
      }
  }

  public record AiToolExecutionResult(boolean success, Object data,
                                      String errorCode, String errorMessage) {
      public static AiToolExecutionResult success(Object data) {
          return new AiToolExecutionResult(true, data, null, null);
      }

      public static AiToolExecutionResult failure(String code, String message) {
          return new AiToolExecutionResult(false, null, code, message);
      }
  }
  ```

  `AiToolExecutor` should be `AiToolExecutionResult execute(String toolCode, Map<String, Object> arguments)`. `AiToolContext` should copy definitions into a code-keyed immutable map and expose `execute` through that executor. A code absent from the context must not be silently executed; return `TOOL_NOT_FOUND` from the context guard.

- [ ] **Step 2: Run the focused tests and verify they fail for missing types.**

  Run:

  ```bash
  mvn -pl ai-platform-ai -am -Dtest=AiToolContextTest,AiConfigurationTest test
  ```

  Expected: compilation failure because the SPI types and new request/config fields do not exist.

- [ ] **Step 3: Implement the contracts.**

  Add the records/interfaces under `com.example.aichat.ai.tool`. `AiToolContext` must contain `Long userId`, `String requestId`, `List<AiToolDefinition> definitions`, and `AiToolExecutor executor`; copy the list and reject duplicate definition codes. Extend `AiChatRequest` with `List<String> toolCodes`, copy it to `List.of()` when null, and retain a six-argument compatibility constructor that supplies no tools.

  Extend `ModelProvider` with:

  ```java
  default Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot,
                                     List<AiMessageDTO> messages,
                                     AiGenerationOptions options) {
      return stream(snapshot, messages, options, null);
  }

  Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot,
                             List<AiMessageDTO> messages,
                             AiGenerationOptions options,
                             AiToolContext toolContext);
  ```

  Add `maxToolRounds = 8` and `maxToolResultBytes = 65_536` to `AiProperties`, with `@Min(1)` validation and `@Validated` on the configuration properties registration. Add `ai.max-tool-rounds: ${AI_MAX_TOOL_ROUNDS:8}` and `ai.max-tool-result-bytes: ${AI_MAX_TOOL_RESULT_BYTES:65536}` to `application.yml`.

  Add these exact error codes:

  ```java
  AI_TOOL_UNSUPPORTED(HttpStatus.BAD_REQUEST, "AI_TOOL_UNSUPPORTED", "The selected AI provider does not support tools"),
  AI_TOOL_LOOP_LIMIT(HttpStatus.BAD_GATEWAY, "AI_TOOL_LOOP_LIMIT", "AI tool calling exceeded the maximum rounds"),
  AI_TOOL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI_TOOL_UNAVAILABLE", "AI tool provider is unavailable"),
  ```

- [ ] **Step 4: Run the focused tests and verify they pass.**

  Run the same Maven command. Expected: PASS, with no dependency from `ai-platform-ai` to `ai-platform-tool`.

- [ ] **Step 5: Commit the contract slice.**

  ```bash
  git add ai-platform-ai/src/main/java/com/example/aichat/ai/tool \
    ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiChatRequest.java \
    ai-platform-ai/src/main/java/com/example/aichat/ai/provider/ModelProvider.java \
    ai-platform-ai/src/main/java/com/example/aichat/ai/config \
    ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java \
    ai-platform-boot/src/main/resources/application.yml \
    ai-platform-ai/src/test/java/com/example/aichat/ai/tool \
    ai-platform-ai/src/test/java/com/example/aichat/ai/config
  git commit -m "feat: add AI tool calling contracts"
  ```

---

### Task 2: Implement the OpenAI-compatible LangChain4j tool loop

**Files:**
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OpenAiToolCallingLoop.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OpenAiModelProvider.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/DefaultAiChatService.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/AiRequestValidator.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OpenAiToolCallingLoopTest.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/core/DefaultAiChatServiceTest.java`

- [ ] **Step 1: Write the failing loop tests.**

  Use a fake `StreamingChatModel` that records `ChatRequest.messages()` and returns two completed turns: first an `AiMessage` containing two `ToolExecutionRequest`s, then an `AiMessage` containing final text. Assert that:

  1. text deltas from the first and final model turns are emitted;
  2. the second `ChatRequest` contains the complete first `AiMessage` followed by two `ToolExecutionResultMessage`s;
  3. both result messages are in model-returned request order even when the executor blocks for different durations;
  4. malformed tool JSON becomes an error tool result and the model is called again;
  5. a tool failure becomes an error tool result rather than terminating the loop;
  6. result JSON larger than `maxToolResultBytes` becomes an error tool result without truncation;
  7. a ninth tool-call round emits `AI_TOOL_LOOP_LIMIT` after eight permitted rounds;
  8. no-tool context keeps the existing `LangChain4jStreamAdapter` path.

  Build tool requests with `ToolExecutionRequest.builder().id(...).name(...).arguments(...)` and final assistant messages with `AiMessage.from(...)`. Assert message types and fields rather than matching serialized JSON strings.

- [ ] **Step 2: Run the new tests and verify they fail.**

  ```bash
  mvn -pl ai-platform-ai -am -Dtest=OpenAiToolCallingLoopTest,DefaultAiChatServiceTest test
  ```

  Expected: compilation failure because the loop and tool-context path do not exist.

- [ ] **Step 3: Implement request construction and response capture.**

  `OpenAiModelProvider` should keep the current builder options (`baseUrl`, resolved API key, model name, timeout, temperature, topP) and construct the same model. When `toolContext == null`, call the existing adapter exactly as before. Otherwise pass the model and initial converted messages to `OpenAiToolCallingLoop`.

  The loop must call the LangChain4j request overload, not the old message-list overload:

  ```java
  ChatRequest request = ChatRequest.builder()
      .messages(messages)
      .toolSpecifications(specifications)
      .build();
  model.doChat(request, handler);
  ```

  Convert each `AiToolDefinition` to a `ToolSpecification` by building JSON containing `name`, `description`, and the definition's `inputSchema`, then calling `ToolSpecification.fromJson(...)`. Do not use raw string matching on tool arguments or schemas.

  Capture each turn's `AiMessage` in the `onCompleteResponse` callback. Forward only non-empty `onPartialResponse` text as `AiStreamEvent.delta`. Propagate provider errors as `AI_PROVIDER_ERROR` through the existing provider error behavior.

- [ ] **Step 4: Implement ordered execution and in-memory history.**

  Keep a new mutable `ArrayList<ChatMessage>` per subscription. For each completed response:

  ```java
  AiMessage assistant = response.aiMessage();
  if (!assistant.hasToolExecutionRequests()) {
      return completionWithoutAddingToolMessages;
  }
  if (toolRounds >= properties.getMaxToolRounds()) {
      return Flux.error(new BusinessException(ErrorCode.AI_TOOL_LOOP_LIMIT));
  }
  messages.add(assistant);
  toolRounds++;
  ```

  Parse every `ToolExecutionRequest.arguments()` with Jackson into `Map<String, Object>` using a `TypeReference`; reject null, array, scalar, and malformed JSON as `TOOL_ARGUMENT_INVALID`. Execute all requests with `Mono.fromCallable(...)` on `Schedulers.boundedElastic()` and `flatMapSequential` so blocking calls may proceed off the model callback thread while result order remains stable. Convert each result to exactly one `ToolExecutionResultMessage.from(request, serializedResult)`; set `isError` for failure results if the builder is needed for the LangChain4j version.

  Serialize successful data as UTF-8 JSON. If serialized bytes exceed `AiProperties.maxToolResultBytes`, create an error result using `TOOL_EXECUTION_FAILED` and the safe message `Tool result exceeds configured limit`; never truncate. Serialize error results as a compact JSON object containing only `success: false`, `errorCode`, and `errorMessage`; never include credentials, full arguments, or full remote responses.

  Append all result messages after the assistant message and recursively/iteratively issue the next `ChatRequest` with the same tool specifications. If the model returns final text, complete the Flux and let `DefaultAiChatService` produce the existing usage/complete event.

- [ ] **Step 5: Wire preflight context resolution into `DefaultAiChatService`.**

  Inject `List<AiToolProvider>` (or an `ObjectProvider<AiToolProvider>`) into `DefaultAiChatService`. After request validation, request ID creation, route, and capability validation, do the following only when `request.toolCodes()` is non-empty:

  ```java
  if (toolProviders.isEmpty()) {
      throw new BusinessException(ErrorCode.AI_TOOL_UNAVAILABLE);
  }
  AiToolContext toolContext = toolProviders.get(0)
      .prepare(request.userId(), requestId, request.toolCodes());
  ```

  Treat provider preparation errors as preflight errors before the first model request. Pass the resulting context to `ModelProvider.stream`; pass null for an absent/empty list. Keep usage logging, text accumulation, final completion, cancellation, and existing no-tool behavior unchanged.

  Extend `AiRequestValidator` to reject more than 20 codes, blank codes, and codes longer than 100 characters when requests originate outside the chat module. The chat module remains responsible for trimming/deduplication at the HTTP boundary.

- [ ] **Step 6: Run the loop tests and existing AI tests.**

  ```bash
  mvn -pl ai-platform-ai -am -Dtest='*Tool*Test,*AiChatServiceTest,OpenAiModelProviderTest,OllamaModelProviderTest' test
  ```

  Expected: PASS. The existing image request test must still make one ordinary `/v1/chat/completions` request without a `tools` field.

- [ ] **Step 7: Commit the AI loop slice.**

  ```bash
  git add ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter \
    ai-platform-ai/src/main/java/com/example/aichat/ai/core \
    ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter \
    ai-platform-ai/src/test/java/com/example/aichat/ai/core
  git commit -m "feat: run OpenAI-compatible AI tool loops"
  ```

---

### Task 3: Preserve Ollama behavior and finish provider error semantics

**Files:**
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/DefaultAiChatService.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/core/DefaultAiChatServiceTest.java`

- [ ] **Step 1: Add failing Ollama tests.**

  Add one test that supplies a non-empty `AiToolContext` and asserts the returned event has code `AI_TOOL_UNSUPPORTED` and that no Ollama HTTP request is made. Keep the existing no-tool tests unchanged and add an assertion that their requests and deltas remain identical.

- [ ] **Step 2: Run the tests and verify the new case fails.**

  ```bash
  mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest,DefaultAiChatServiceTest test
  ```

- [ ] **Step 3: Add the explicit rejection branch.**

  In `OllamaModelProvider.stream(..., AiToolContext toolContext)`, return `Flux.just(AiStreamEvent.error(ErrorCode.AI_TOOL_UNSUPPORTED.code(), ErrorCode.AI_TOOL_UNSUPPORTED.message()))` before constructing the Ollama model when the context is non-null. Otherwise execute the current builder and `LangChain4jStreamAdapter` path without changing timeout or generation option handling.

- [ ] **Step 4: Run provider regression tests.**

  ```bash
  mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest,OpenAiModelProviderTest,DefaultAiChatServiceTest test
  ```

  Expected: PASS, with no-tools behavior unchanged for all providers.

- [ ] **Step 5: Commit the provider compatibility slice.**

  ```bash
  git add ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java \
    ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java \
    ai-platform-ai/src/test/java/com/example/aichat/ai/core/DefaultAiChatServiceTest.java
  git commit -m "fix: reject tools explicitly for Ollama"
  ```

---

### Task 4: Implement the tool bridge and HTTP schema generator

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/ai/ToolAiToolProvider.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/ai/HttpToolSchemaGenerator.java`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/config/ToolConfiguration.java`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/ToolExecutionService.java`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImpl.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/ai/HttpToolSchemaGeneratorTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/ai/ToolAiToolProviderTest.java`

- [ ] **Step 1: Write failing HTTP schema tests.**

  Construct `HttpToolConfig` with the design's query example and assert the generated root schema contains only `city`, with type `string` and `required: [city]`; `count` and `language` must not appear. Add tests for `weather-{{city}}`, nested `{{customer.id}}`, repeated placeholders, a full body placeholder preserving an unconstrained JSON property, and static body/query values. Assert a no-placeholder HTTP config returns an empty object schema.

  Add a test with this body template:

  ```json
  {
    "city": "{{city}}",
    "count": "{{count}}",
    "includeForecast": "{{includeForecast}}",
    "filters": "{{filters}}"
  }
  ```

  The generated `count`, `includeForecast`, and `filters` properties must not force string conversion; the generator must produce schema that accepts JSON values for complete body placeholders. The generator must never mutate `HttpToolConfig` or `TemplateValueResolver` behavior.

- [ ] **Step 2: Run schema tests and verify they fail.**

  ```bash
  mvn -pl ai-platform-tool -am -Dtest=HttpToolSchemaGeneratorTest test
  ```

  Expected: compilation failure because the generator does not exist.

- [ ] **Step 3: Implement recursive placeholder discovery.**

  Reuse the same placeholder grammar as `TemplateValueResolver` (`{{root.segment}}`) in a private scanner. Walk only `queryParams`, ordinary `headers`, and `bodyTemplate`; never walk URL, method, auth fields, `ToolCredentialHeaders`, or saved secret headers. For query/header placeholders use a string schema, including complete matches, because the existing executor converts these protocol values to strings. For complete body matches use an unconstrained JSON schema (`{}`) so numbers, booleans, objects, and arrays survive; for embedded body matches use string schema.

  Add nested object properties for dotted names. Merge duplicate occurrences into one root property and preserve deterministic insertion order. Mark every discovered root and nested parameter required because every placeholder occurrence is required by existing template resolution. Detect conflicting scalar/object paths and throw `BusinessException(ErrorCode.TOOL_CONFIG_INVALID, ...)` rather than emitting an ambiguous schema.

  Return a Jackson `ObjectNode` with `type: object`, `properties`, and `required`; it is consumed by the AI SPI and does not add a database field.

- [ ] **Step 4: Write failing bridge/preflight tests.**

  Mock `ToolDefinitionMapper`, `McpServerManagementService`, `ToolExecutionService`, and `CurrentUserProvider`/Sa-Token boundary. Assert that `prepare(userId, requestId, List.of(...))`:

  1. looks up exactly the requested codes and returns definitions in request order;
  2. rejects unknown, disabled, stale, and disabled-MCP-server definitions with the existing codes;
  3. uses generated HTTP schema and saved MCP `inputSchema` without exposing auth/credential fields;
  4. rejects invalid MCP schema as `TOOL_CONFIG_INVALID`;
  5. does not expose any unrequested definition;
  6. calls the user-scoped execution method with the original request ID, tool code, and parsed argument map;
  7. converts a `BusinessException` into a sanitized failed `AiToolExecutionResult` instead of throwing into the model loop.

- [ ] **Step 5: Add a user-scoped execution entry point and authorization.**

  Extend `ToolExecutionService` with:

  ```java
  ToolExecutionResult execute(ToolExecuteRequest request);
  ToolExecutionResult executeAsUser(Long userId, ToolExecuteRequest request);
  ```

  Keep the controller calling `execute(request)`. Both paths must pass through one private implementation that calls `StpUtil.checkPermission("tool:execute")` before loading the definition. `executeAsUser` must use `StpUtil.switchTo(userId, () -> execute(request))`, so `CurrentUserProvider`, audit `userId`, credentials, and executor behavior see the authenticated chat user even when invoked on `boundedElastic`. Do not implement a second execution path in the bridge.

  Preserve all existing status, availability, MCP-server, timeout, audit, and safe-message behavior. Add a focused service test proving that a user without `tool:execute` is rejected for both controller-style and AI-style entry points and that an authorized call records the switched user ID.

- [ ] **Step 6: Implement the bridge.**

  `ToolAiToolProvider` must implement `AiToolProvider` and be a Spring component. In `prepare`, trim and validate the incoming codes, switch to `userId`, check `tool:execute`, load each non-deleted definition, verify status and availability, verify MCP server status, create the appropriate schema, and build `AiToolDefinition` values. Return an `AiToolContext` whose executor parses already-structured arguments only at the tool-service boundary and calls `executeAsUser` with the captured user and request ID. The bridge may use `ObjectMapper.convertValue` to make a defensive `Map<String,Object>` copy, but it must not parse serialized tool JSON or duplicate HTTP/MCP execution logic.

  For execution results, serialize only the successful `data` or an error object; use the existing tool error code and the service's sanitized message. Never put credentials, full request parameters, or complete remote responses in an AI error message.

  For MCP tools, parse `ToolDefinition.inputSchema` as a JSON object and pass it unchanged as the SPI schema. For HTTP tools, call `HttpToolSchemaGenerator` on `httpConfig`. Reject malformed configuration/schema with `TOOL_CONFIG_INVALID` during preflight or tool-result conversion as specified by the failing test.

- [ ] **Step 7: Register the bridge and run tool tests.**

  Register `HttpToolSchemaGenerator` and `ToolAiToolProvider` through component scanning or explicit beans in `ToolConfiguration`. Run:

  ```bash
  mvn -pl ai-platform-tool -am -Dtest='*ToolAiToolProviderTest,HttpToolSchemaGeneratorTest,ToolExecutionServiceImplTest,TemplateValueResolverTest,HttpToolExecutorTest,McpToolExecutorTest' test
  ```

  Expected: PASS, with all existing tool-execution tests still passing.

- [ ] **Step 8: Commit the tool bridge slice.**

  ```bash
  git add ai-platform-tool/src/main/java/com/example/aichat/tool/ai \
    ai-platform-tool/src/main/java/com/example/aichat/tool/config/ToolConfiguration.java \
    ai-platform-tool/src/main/java/com/example/aichat/tool/service \
    ai-platform-tool/src/test/java/com/example/aichat/tool/ai \
    ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImplTest.java
  git commit -m "feat: bridge executable tools into AI"
  ```

---

### Task 5: Propagate explicit tool codes through chat and regenerate

**Files:**
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRequestDTO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRegenerateDTO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImpl.java`
- Test: `ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatServiceImplTest.java`
- Test: `ai-platform-chat/src/test/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImplTest.java`

- [ ] **Step 1: Write failing propagation tests.**

  Add tests that submit `List.of(" weather ", "weather", "forecast")` and assert the command/request receives `weather, forecast` in that order. Add tests that a blank/whitespace code raises `VALIDATION_ERROR`, that more than 20 codes or a code over 100 characters is rejected by Jakarta validation, and that regenerate uses only its explicit list rather than the historical message's tool list. Add a no-tool test asserting an empty list reaches `AiChatRequest`.

- [ ] **Step 2: Run the chat tests and verify they fail.**

  ```bash
  mvn -pl ai-platform-chat -am -Dtest=ChatServiceImplTest,AiChatGatewayImplTest test
  ```

  Expected: compilation failure because the DTO, command, and AI request signatures do not carry tool codes.

- [ ] **Step 3: Extend DTOs and compatibility constructors.**

  Add the final record component `List<@Size(max = 100) String> toolCodes` to both request DTOs and annotate the list with `@Size(max = 20)`. Keep the existing convenience constructors and supply `List.of()` for the new field so current callers compile. Add a final `List<String> toolCodes` component to `ChatGenerationCommand`, copy it in the canonical constructor, and preserve both existing overloads by delegating with `List.of()`.

- [ ] **Step 4: Normalize at the chat service boundary.**

  Add one private method in `ChatServiceImpl`:

  ```java
  private List<String> normalizeToolCodes(List<String> toolCodes) {
      if (toolCodes == null) return List.of();
      List<String> normalized = new ArrayList<>();
      for (String code : toolCodes) {
          if (code == null || code.isBlank()) {
              throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tool code must not be blank");
          }
          String value = code.trim();
          if (value.length() > 100) {
              throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tool code is too long");
          }
          if (!normalized.contains(value)) normalized.add(value);
      }
      if (normalized.size() > 20) {
          throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At most 20 tool codes are allowed");
      }
      return List.copyOf(normalized);
  }
  ```

  Call it for both `stream` and `regenerate`. Do not read or infer tool codes from chat history. Pass the result into `ChatGenerationCommand`.

- [ ] **Step 5: Forward through the gateway.**

  In `AiChatGatewayImpl`, construct `AiChatRequest` with `command.toolCodes()` after the message list and before/alongside generation options according to the final record signature. No tool definitions, credentials, or execution details should enter the chat module.

- [ ] **Step 6: Run chat regression tests.**

  ```bash
  mvn -pl ai-platform-chat -am -Dtest='*ChatServiceImplTest,*AiChatGatewayImplTest,*ChatModelControllerTest' test
  ```

  Expected: PASS. Existing text, image, knowledge, regenerate, and SSE tests must continue to compile and pass with empty tool lists.

- [ ] **Step 7: Commit the chat propagation slice.**

  ```bash
  git add ai-platform-chat/src/main/java/com/example/aichat/chat/dto \
    ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java \
    ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java \
    ai-platform-chat/src/main/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImpl.java \
    ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatServiceImplTest.java \
    ai-platform-chat/src/test/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImplTest.java
  git commit -m "feat: pass explicit tool codes through chat"
  ```

---

### Task 6: Verify end-to-end in-memory behavior without changing persistence or SSE

**Files:**
- Modify only if tests expose a real defect: `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatStreamOrchestrator.java`, `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatRegenerateOrchestrator.java`, or `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatGenerationFinalizer.java`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/application/ChatStreamOrchestratorToolCallingTest.java`
- Inspect: `ai-platform-chat/src/main/java/com/example/aichat/chat/entity/ChatMessage.java`, `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatGenerationFinalizer.java`

- [ ] **Step 1: Add a failing orchestration regression test.**

  Stub `AiChatGateway` to emit `START`, multiple text `DELTA`s spanning a tool pause, and `COMPLETE`. Assert the orchestrator emits only the existing `START`, `DELTA`, `COMPLETE`, and `FAILED` event types, finalizes only the final visible text, and does not persist assistant tool-call or tool-result messages. Assert a `BusinessException` from AI preflight becomes the existing `FAILED` event with its stable code/message.

- [ ] **Step 2: Run the test and inspect any failure before editing.**

  ```bash
  mvn -pl ai-platform-chat -am -Dtest=ChatStreamOrchestratorToolCallingTest test
  ```

- [ ] **Step 3: Keep orchestration unchanged unless the test proves otherwise.**

  The expected implementation is no production change: tool execution is internal to `AiChatService`, so the orchestrator should continue collecting only `AiStreamEvent.DELTA`, finalizing at `COMPLETE`, and using its existing error mapping. If a failure is found, make the smallest change that preserves the current event contract; do not add tool-specific SSE events or database fields.

- [ ] **Step 4: Run chat module regression tests.**

  ```bash
  mvn -pl ai-platform-chat -am test
  ```

  Expected: PASS, and no migration or mapper XML changes.

- [ ] **Step 5: Commit only if production orchestration changed.**

  ```bash
  git status --short
  git add ai-platform-chat/src/main/java ai-platform-chat/src/test/java
  git commit -m "test: preserve chat stream contract during tool calls"
  ```

  If only the test was needed and the repository policy keeps tests with Task 5, include it in the Task 5 commit instead of creating a no-op commit.

---

### Task 7: Run module and root verification, then fix only proven defects

**Files:**
- Modify only files identified by failing verification tests.
- Do not modify: chat schema migrations, tool schema migrations, SSE event enums, or model-management capability fields.

- [ ] **Step 1: Run targeted module suites in dependency order.**

  ```bash
  mvn -pl ai-platform-ai -am test
  mvn -pl ai-platform-tool -am test
  mvn -pl ai-platform-chat -am test
  ```

  Expected: all three commands exit 0. Record any failure with its first application stack frame before changing code.

- [ ] **Step 2: Run the root Maven test suite.**

  ```bash
  mvn test
  ```

  Expected: all existing module tests and new tests pass. Do not skip tests, disable hooks, or use a narrower profile to hide failures.

- [ ] **Step 3: Run a compile/dependency check.**

  ```bash
  mvn -DskipTests compile
  mvn -q -pl ai-platform-ai dependency:tree -Dincludes=com.example.aichat:ai-platform-tool
  ```

  Expected: AI module has no `ai-platform-tool` dependency; `ai-platform-tool` depends on AI only in the declared direction.

- [ ] **Step 4: Inspect the final diff for security and scope.**

  ```bash
  git diff master...HEAD --stat
  git diff master...HEAD -- ai-platform-ai ai-platform-tool ai-platform-chat ai-platform-common ai-platform-boot
  ```

  Confirm that credentials, full HTTP arguments, full remote responses, and internal tool-call messages are not emitted through chat SSE or persisted; all AI tool executions go through `ToolExecutionService`; HTTP URL/SSRF/timeout behavior remains in the existing executor; and no unrelated refactoring was introduced.

- [ ] **Step 5: Report verification evidence.**

  Include the exact module and root commands, pass/fail counts, any known environment limitation, and the final changed-file summary. Do not claim completion until the verification command output is available.

---

## Self-Review Checklist

- **Spec coverage:** The plan covers explicit `toolCodes`, AI SPI, provider discovery, OpenAI/DeepSeek/Qwen looping, Ollama rejection, HTTP and MCP schemas, multiple ordered tool calls, JSON argument parsing, result-size errors, round limits, user authorization, existing execution/audit/security controls, regenerate propagation, no persistence/SSE changes, and targeted/root tests.
- **Dependency direction:** The AI SPI contains only provider-neutral records and Jackson/LangChain-facing types. The tool implementation depends on AI; AI never imports tool entities or services.
- **Compatibility:** Existing no-tool `ModelProvider.stream` and DTO/command constructors remain available, while no-tool requests use the unchanged adapter path.
- **Error semantics:** Preflight uses `TOOL_NOT_FOUND`, `TOOL_DISABLED`, `TOOL_STALE`, `TOOL_CONFIG_INVALID`, `AI_TOOL_UNAVAILABLE`, or provider-specific unsupported errors; execution failures become model-visible tool results instead of aborting the loop.
- **Security:** `tool:execute` is checked inside the shared execution service, the AI bridge captures the authenticated user, and tool results/errors are bounded and sanitized.
- **Scope:** No model capability database field, Ollama tool support, manual approval, tool SSE events, chat persistence, HTTP schema database field, auto-selection, or session-level binding is added.
