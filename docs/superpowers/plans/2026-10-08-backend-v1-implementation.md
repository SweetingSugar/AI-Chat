# AI Chat 后端 V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the six-module Java 17 modular monolith that provides RBAC-protected, persisted, SSE-streamed AI chat.

**Architecture:** The root Maven project owns dependency versions and aggregates six modules. `common` exposes cross-cutting contracts; `system`, `ai`, `chat`, and `admin` implement separate bounded contexts, while `boot` is the only executable assembly. Chat validates ownership and persists state before and after invoking the `AiChatService` abstraction.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Web MVC, WebFlux `Flux` for SSE, MyBatis-Plus 3.5.17, MySQL/Flyway, Redis/Sa-Token 1.46.0, LangChain4j 1.21.0, JUnit 5, Mockito, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-08-backend-v1-design.md`

## Global Constraints

- Use Java 17+ and Spring Boot 3.x; package all production code under `com.example.aichat`.
- The Maven graph is `boot → admin/chat/system/ai → common`, with only `chat → ai`; no cycles or reverse domain dependencies.
- Organize packages by domain aggregate; never create cross-domain global controller/service/mapper packages.
- Controller code must not access a Mapper or any LangChain4j model; AI calls only go through `AiChatService`.
- Authenticate with Sa-Token and authorize administrative operations with `@SaCheckPermission`.
- Every conversation and message operation must establish ownership from the logged-in user before accessing data.
- Use MyBatis-Plus for MySQL access, Redis for Sa-Token sessions and permission cache, and SSE for AI output.
- Keep deployment monolithic; do not add Spring Cloud or remote service calls.
- Each module receives unit and integration tests; do not begin the next task until the preceding task compiles and tests pass.
- Never commit secrets. Configuration uses environment-variable references and test-only dynamic container properties.

## Review Focus

- Blank, overlong, or duplicated usernames are rejected before database writes; covered by Task 2's request validation tests.
- A revoked role must not remain usable through a stale permission cache; covered by Task 2's Redis cache invalidation integration test.
- A syntactically valid conversation ID owned by another user returns the same not-found error as an absent ID; covered by Task 4's ownership integration test.
- A provider exception or disconnected SSE subscriber persists the partial assistant text with a terminal status rather than leaving `GENERATING`; covered by Task 4's streaming failure and cancellation tests.
- Empty or excessively long chat text produces a validation error before an AI request; covered by Task 4's message-command validation test.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| `pom.xml` | Aggregator, BOMs, plugin management, Enforcer and module list |
| `ai-platform-common` | API envelope, business errors, exception handler, current-user port |
| `ai-platform-system` | User/RBAC persistence, login application service, Sa-Token adapters, V1 schema migration |
| `ai-platform-ai` | Provider-neutral command/event types, `AiChatService`, LangChain4j adapter and fake test adapter |
| `ai-platform-chat` | Conversation/message aggregates, MyBatis-Plus persistence, ownership policy, REST/SSE controllers, V2 migration |
| `ai-platform-admin` | Administration and dashboard endpoints, operation-log persistence, V3 migration |
| `ai-platform-boot` | `@SpringBootApplication`, concrete wiring, profiles, migrations, health and full-stack tests |
| `src/**` at repository root | Remove after its replacement exists; root may not contain application code |

### Task 1: Maven reactor and common contracts

**Files:**
- Modify: `pom.xml`
- Delete: `src/main/java/com/example/aichat/AiChatApplication.java`, `src/main/resources/application.yml`, `src/test/java/com/example/aichat/AiChatApplicationTests.java`
- Create: `ai-platform-common/pom.xml`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/api/ApiResponse.java`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/error/BusinessException.java`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/web/GlobalExceptionHandler.java`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/security/CurrentUser.java`
- Create: `ai-platform-common/src/main/java/com/example/aichat/common/security/CurrentUserProvider.java`
- Create: `ai-platform-common/src/test/java/com/example/aichat/common/web/GlobalExceptionHandlerTest.java`
- Create: `ai-platform-common/src/test/java/com/example/aichat/common/security/CurrentUserProviderContractTest.java`

**Interfaces:**
- Consumes: no application module.
- Produces: `ApiResponse.success(T)`, `ApiResponse.failure(ErrorCode, String)`, `BusinessException(ErrorCode)`, `CurrentUser(long id, String username)`, and `CurrentUserProvider.requireCurrentUser()`.

- [ ] **Step 1: Write the failing common-contract tests**

Test that a `BusinessException(CHAT_CONVERSATION_NOT_FOUND)` is converted to an API body with that exact code, and that an unauthenticated `CurrentUserProvider` implementation contract raises `UNAUTHENTICATED`.

- [ ] **Step 2: Run the common tests to verify they fail**

Run: `mvn -pl ai-platform-common test`

Expected: FAIL because the module and contract classes do not exist.

- [ ] **Step 3: Replace the root application POM with the parent reactor and create `common`**

Set root `groupId` to `com.example.aichat`, `artifactId` to `ai-platform-parent`, and packaging to `pom`. Add the six modules, import `spring-boot-dependencies:3.5.16` and `langchain4j-bom:1.21.0`, pin MyBatis-Plus `3.5.17` and Sa-Token `1.46.0`, and configure compiler release 17, Surefire, Failsafe, and Maven Enforcer Java-version validation. Add `spring-boot-starter-web`, validation, and test dependencies only to the modules that need them; root only manages versions.

Implement immutable response/error/current-user records and a `@RestControllerAdvice`. Define at least `UNAUTHENTICATED`, `FORBIDDEN`, `VALIDATION_ERROR`, `CHAT_CONVERSATION_NOT_FOUND`, `AI_PROVIDER_ERROR`, and `INTERNAL_ERROR`; map unexpected errors to `INTERNAL_ERROR` without stack-trace exposure.

- [ ] **Step 4: Run common tests and module compilation**

Run: `mvn -pl ai-platform-common -am test`

Expected: PASS, with Java 17 enforcement active.

- [ ] **Step 5: Commit the common foundation**

```bash
git add pom.xml ai-platform-common src
git commit -m "feat: establish multi-module common contracts"
```

### Task 2: System domain, Sa-Token login, and RBAC

**Files:**
- Create: `ai-platform-system/pom.xml`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/user/domain/User.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/user/infrastructure/persistence/UserMapper.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/auth/application/AuthApplicationService.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/auth/api/AuthController.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/rbac/application/PermissionQueryService.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/rbac/api/AdminUserController.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/rbac/api/AdminRoleController.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/rbac/api/AdminPermissionController.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/security/SaTokenCurrentUserProvider.java`
- Create: `ai-platform-system/src/main/java/com/example/aichat/system/security/SaTokenPermissionAdapter.java`
- Create: `ai-platform-system/src/main/resources/db/migration/V1__system_schema.sql`
- Create: `ai-platform-system/src/test/java/com/example/aichat/system/auth/application/AuthApplicationServiceTest.java`
- Create: `ai-platform-system/src/test/java/com/example/aichat/system/rbac/application/PermissionQueryServiceIntegrationTest.java`
- Create: `ai-platform-system/src/test/java/com/example/aichat/system/auth/api/AuthControllerTest.java`

**Interfaces:**
- Consumes: `CurrentUserProvider` and `BusinessException` from `common`.
- Produces: `AuthApplicationService.login(LoginCommand): LoginResult`, `AuthApplicationService.logout(): void`, `PermissionQueryService.permissionsOf(long): Set<String>`, and a `SaTokenPermissionAdapter` registered in the boot module.

- [ ] **Step 1: Write the failing auth and RBAC tests**

Use a mocked user repository to assert BCrypt password verification, disabled-account rejection, and Sa-Token login issuance. Use a MySQL Testcontainer plus Flyway V1 migration to assert the role/permission join result and that role mutation evicts `permission:{userId}` from Redis. In MockMvc, assert unauthenticated `/api/auth/me` returns `UNAUTHENTICATED` and a protected administrator endpoint rejects users missing `system:user:read`.

- [ ] **Step 2: Run system tests to verify they fail**

Run: `mvn -pl ai-platform-system -am test`

Expected: FAIL because the system module and migration do not exist.

- [ ] **Step 3: Implement system entities, schema, authentication, and RBAC**

Implement MyBatis-Plus entities/mappers only under their owning aggregates. In V1 create `sys_user`, `sys_role`, `sys_permission`, `sys_user_role`, and `sys_role_permission` with the unique keys named in the spec. `POST /api/auth/login` accepts `username` and `password`, uses BCrypt, and calls `StpUtil.login(userId)` only after account-status validation. `POST /api/auth/logout` logs out the current token; `GET /api/auth/me` returns only the current user profile.

Expose role, permission, and user administration endpoints with explicit `@SaCheckPermission` values `system:user:*`, `system:role:*`, and `system:permission:*`. On every association mutation, delete the affected users' permission-cache keys. Seed a disabled regular user and a single bootstrap administrator from environment-configured credentials in a repeatable, idempotent bootstrap component.

- [ ] **Step 4: Run all system tests and compilation**

Run: `mvn -pl ai-platform-system -am test`

Expected: PASS, including Testcontainer-backed RBAC integration coverage when Docker is available.

- [ ] **Step 5: Commit system authentication and RBAC**

```bash
git add ai-platform-system pom.xml
git commit -m "feat: add system login and rbac"
```

### Task 3: AI domain and LangChain4j boundary

**Files:**
- Create: `ai-platform-ai/pom.xml`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/chat/AiChatService.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/chat/AiChatRequest.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/chat/AiChatDelta.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/context/ChatContextWindow.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/LangChain4jAiChatService.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/AiModelProperties.java`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/context/ChatContextWindowTest.java`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/LangChain4jAiChatServiceTest.java`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/support/FakeAiChatService.java`

**Interfaces:**
- Consumes: `common` only.
- Produces: `Flux<AiChatDelta> AiChatService.stream(AiChatRequest request)` and `ChatContextWindow.trim(List<AiChatMessage>, int maxCharacters): List<AiChatMessage>`.

- [ ] **Step 1: Write the failing AI tests**

Test that `ChatContextWindow` keeps the newest complete user/assistant turns within its character budget and retains the system message. Test that the adapter maps ordered streaming callback tokens to `AiChatDelta.text(String)` and maps a provider error to `AiChatDelta.failure(Throwable)` without leaking credentials. Test-only `FakeAiChatService` must emit caller-specified deltas and errors deterministically.

- [ ] **Step 2: Run AI tests to verify they fail**

Run: `mvn -pl ai-platform-ai -am test`

Expected: FAIL because the AI contracts and adapter do not exist.

- [ ] **Step 3: Implement the provider-neutral AI service**

Add `langchain4j` and `langchain4j-open-ai` managed by the root BOM. Keep model construction in `LangChain4jAiChatService` using `AiModelProperties` (`baseUrl`, `apiKey`, `modelName`, `timeout`); never expose LangChain4j types through `AiChatService`. Adapt callback-based streaming to a Reactor `Flux` with cancellation propagation. Do not use an `@AiService` HTTP-facing interface.

- [ ] **Step 4: Run AI tests and module compilation**

Run: `mvn -pl ai-platform-ai -am test`

Expected: PASS with no network request or real provider key required.

- [ ] **Step 5: Commit AI abstraction**

```bash
git add ai-platform-ai pom.xml
git commit -m "feat: add langchain4j chat service boundary"
```

### Task 4: Chat domain, ownership boundary, persistence, and SSE

**Files:**
- Create: `ai-platform-chat/pom.xml`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/conversation/domain/Conversation.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/message/domain/ChatMessage.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/conversation/infrastructure/persistence/ConversationMapper.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/message/infrastructure/persistence/ChatMessageMapper.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/conversation/application/ConversationApplicationService.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/message/application/ChatStreamingApplicationService.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/conversation/api/ConversationController.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/message/api/ChatStreamController.java`
- Create: `ai-platform-chat/src/main/resources/db/migration/V2__chat_schema.sql`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/message/application/ChatStreamingApplicationServiceTest.java`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/conversation/application/ConversationOwnershipIntegrationTest.java`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/message/api/ChatStreamControllerTest.java`

**Interfaces:**
- Consumes: `CurrentUserProvider`, `AiChatService.stream(AiChatRequest)`, and common errors.
- Produces: `ConversationApplicationService.getOwned(long conversationId, long userId): Conversation`, `ChatStreamingApplicationService.stream(SendMessageCommand): Flux<ChatSseEvent>`, and the chat REST/SSE endpoints.

- [ ] **Step 1: Write the failing chat tests**

Test that `getOwned` queries a non-deleted conversation by both ID and `user_id` and reports `CHAT_CONVERSATION_NOT_FOUND` for both absent and foreign IDs. In a transactional service test, assert user message creation precedes assistant `GENERATING` creation; assert fake deltas become ordered `message` events and a final `done`, and persistence becomes `COMPLETED`. Assert fake-provider failure and Flux cancellation retain partial content and write `FAILED` or `CANCELLED`. Test blank/overlong text rejection. In MockMvc, assert the stream controller has `text/event-stream` content type and delegates only to the application service.

- [ ] **Step 2: Run chat tests to verify they fail**

Run: `mvn -pl ai-platform-chat -am test`

Expected: FAIL because chat code and V2 migration do not exist.

- [ ] **Step 3: Implement conversations, messages, and streaming orchestration**

In V2 create `chat_conversation` with `(user_id, last_message_at)` index and logical-delete metadata; create `chat_message` with `(conversation_id, sequence_no)` uniqueness, role, model name, content, and terminal status. Keep all mapper calls in application/infrastructure classes. Have every route resolve the user once through `CurrentUserProvider` and call `getOwned` before reading or writing its conversation.

`ChatStreamingApplicationService.stream(SendMessageCommand)` must transactionally reserve the user and assistant messages, then invoke only `AiChatService`. Emit `message`, `done`, and `error` event DTOs exactly as the spec defines. Serialize writes per conversation (a per-conversation lock or an optimistic sequence retry) and use a new transaction for the terminal assistant update so asynchronous stream completion is persisted. Controller methods return `Flux<ServerSentEvent<ChatSseEvent>>` and do no persistence or model work.

- [ ] **Step 4: Run chat tests and module compilation**

Run: `mvn -pl ai-platform-chat -am test`

Expected: PASS, including ownership and stream terminal-state tests.

- [ ] **Step 5: Commit chat and SSE support**

```bash
git add ai-platform-chat pom.xml
git commit -m "feat: add owned chat conversations and sse streaming"
```

### Task 5: Admin statistics and operation logs

**Files:**
- Create: `ai-platform-admin/pom.xml`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/statistics/application/AdminStatisticsService.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/statistics/api/AdminStatisticsController.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/operationlog/domain/OperationLog.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/operationlog/infrastructure/persistence/OperationLogMapper.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/operationlog/application/OperationLogQueryService.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/operationlog/api/OperationLogController.java`
- Create: `ai-platform-admin/src/main/resources/db/migration/V3__admin_schema.sql`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/statistics/application/AdminStatisticsServiceTest.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/statistics/api/AdminStatisticsControllerTest.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/operationlog/application/OperationLogQueryServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `system` and `chat` public application/query services, common response/errors.
- Produces: `AdminStatisticsService.overview(): OverviewStatistics` and RBAC-protected statistics/log endpoints.

- [ ] **Step 1: Write the failing admin tests**

Test that overview returns user, conversation, message counts and daily aggregate rows without content fields. Test that `/api/admin/statistics/overview` requires `admin:statistics:read`, and operation-log listing requires `admin:operation-log:read`. With the V3 migration and MySQL Testcontainer, assert filters paginate by created time/action/operator and never serialize a chat message's text.

- [ ] **Step 2: Run admin tests to verify they fail**

Run: `mvn -pl ai-platform-admin -am test`

Expected: FAIL because admin code and V3 migration do not exist.

- [ ] **Step 3: Implement admin query services and logs**

Create `sys_operation_log` in V3 with immutable audit fields. Gather statistics through explicitly exposed query services or focused read mappers rather than reaching into controller code. Add `@SaCheckPermission` checks, paginated DTOs, and an operation-log aspect/interceptor that records administrative mutations but redacts request fields named password, token, secret, and apiKey.

- [ ] **Step 4: Run admin tests and module compilation**

Run: `mvn -pl ai-platform-admin -am test`

Expected: PASS, including authorization and no-content-leakage assertions.

- [ ] **Step 5: Commit admin functionality**

```bash
git add ai-platform-admin pom.xml
git commit -m "feat: add admin statistics and operation logs"
```

### Task 6: Boot assembly, operational configuration, and end-to-end verification

**Files:**
- Create: `ai-platform-boot/pom.xml`
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/SecurityConfiguration.java`
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/AiConfiguration.java`
- Create: `ai-platform-boot/src/main/resources/application.yml`
- Create: `ai-platform-boot/src/main/resources/application-test.yml`
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/AiPlatformApplicationIT.java`
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/ChatFlowIT.java`
- Create: `.env.example`
- Modify: `.gitignore`
- Create: `README.md`

**Interfaces:**
- Consumes: all five library modules and their migrations/configuration properties.
- Produces: the sole executable Spring Boot JAR, `health` endpoint, documented environment contract, and full-stack test profile.

- [ ] **Step 1: Write failing full-stack integration tests**

Use `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `MySQLContainer` and `GenericContainer` Redis properties. Assert Flyway applies V1-V3, a login token accesses its own created conversation, a fake configured AI service produces SSE `message` followed by `done`, and a second account receives `CHAT_CONVERSATION_NOT_FOUND` for the first account's conversation. Add a context-only test that asserts the production profile does not start without required model configuration.

- [ ] **Step 2: Run boot integration tests to verify they fail**

Run: `mvn -pl ai-platform-boot -am verify`

Expected: FAIL because the executable assembly and test configuration do not exist.

- [ ] **Step 3: Assemble the application and externalize configuration**

Make `ai-platform-boot` the only module with `spring-boot-maven-plugin` repackage. Component-scan all application packages, configure MyBatis-Plus mapper scanning and Flyway, expose `/actuator/health`, and register the production `SaTokenCurrentUserProvider`, `SaTokenPermissionAdapter`, and `LangChain4jAiChatService`. The test profile must select `FakeAiChatService` only.

Use `${MYSQL_URL}`, `${MYSQL_USERNAME}`, `${MYSQL_PASSWORD}`, `${REDIS_HOST}`, `${REDIS_PASSWORD}`, `${SA_TOKEN_SIGNING_KEY}`, `${AI_BASE_URL}`, `${AI_API_KEY}`, and `${AI_MODEL_NAME}` without real values in committed YAML. Add `.env.example` with names/placeholders only and ignore `.env`. Document setup, migration, test commands, login/permission bootstrap, and the SSE event protocol in the README.

- [ ] **Step 4: Run complete reactor verification**

Run: `mvn verify`

Expected: PASS. If Docker is unavailable, the documented `-DskipITs` profile may skip only Testcontainer integration tests; all unit tests must still pass.

- [ ] **Step 5: Run packaged-application smoke test**

Run: `mvn -pl ai-platform-boot -am package && java -jar ai-platform-boot/target/ai-platform-boot-*.jar --spring.profiles.active=test`

Expected: application starts, logs Flyway completion, and exposes a healthy application context using test containers/configuration.

- [ ] **Step 6: Commit the boot module and operational documentation**

```bash
git add ai-platform-boot .env.example .gitignore README.md pom.xml
git commit -m "feat: assemble ai platform boot application"
```

## Final Review Task: branch-wide verification

**Files:**
- Modify only if a failing verification or review finding requires a minimal documented fix.

**Interfaces:**
- Consumes: all tasks above.
- Produces: a clean, fully verified V1 branch.

- [ ] **Step 1: Inspect the Maven dependency graph**

Run: `mvn -q dependency:tree -Dincludes=com.example.aichat -DskipTests`

Expected: `boot` depends on modules only in the specified direction; no domain cycle or unexpected reverse dependency.

- [ ] **Step 2: Run clean full verification**

Run: `mvn clean verify`

Expected: PASS with all available module unit and integration tests.

- [ ] **Step 3: Inspect security and persistence invariants manually**

Check every `@RequestMapping` under `chat` calls an application service, every Mapper invocation sits outside controllers, all conversation lookup methods contain user ID ownership criteria, and no model key/token/password occurs in logs or committed config.

- [ ] **Step 4: Commit only any verified final fixes**

```bash
git status --short
git add -p
git commit -m "fix: address final backend verification findings"
```

Skip this step if the tree is clean.

## Plan Self-Review

- Spec coverage: module topology and direction (Task 1); authentication/RBAC/cache (Task 2); LangChain4j and context (Task 3); MySQL chat ownership/SSE/terminal states (Task 4); administration/logging (Task 5); Redis, Flyway, configuration, deployment and full-system test (Task 6).
- Type consistency: Task 3 defines the only AI entry point consumed by Task 4; Task 1 defines the only user-context contract used in Tasks 2 and 4; boot wires implementations without becoming a business module.
- Review-focus coverage: each of the five operationally sensitive cases is assigned to an explicit test in its owning task.
- Proportion: tasks are bounded to independently compilable modules and require a green Maven test cycle before progressing.
