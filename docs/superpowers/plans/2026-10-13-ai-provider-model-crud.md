# AI 供应商与模型管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为后台管理员交付安全的 AI 供应商与模型 REST CRUD，并使新配置可供现有模型路由使用。

**Architecture:** `ai-platform-ai` 持有实体、持久化、应用服务和 AES-GCM 凭据边界；`ai-platform-admin` 只暴露经权限保护的管理 REST 接口。供应商 API Key 在写服务处加密、在现有模型适配器调用前解密，任何响应对象都不包含密钥或密文。

**Tech Stack:** Java 17、Spring Boot 3、MyBatis-Plus、Flyway、Sa-Token、JUnit 5、Mockito。

**Spec:** `docs/superpowers/specs/2026-10-13-ai-provider-model-crud-design.md`

## Global Constraints

- API 路径固定为 `/api/admin/ai/providers` 和 `/api/admin/ai/models`，返回 `ApiResponse<T>`。
- Controller 只放在 admin 模块的 `com.example.aichat.admin.controller`；AI 模块不得依赖 admin。
- API Key 只接受于供应商写请求；任何响应、异常和日志不得暴露明文、密文或主密钥。
- `AI_CREDENTIAL_ENCRYPTION_KEY` 是 AES-GCM 主密钥的唯一配置来源；空值或非法值不得触发明文回退。
- 供应商编码、以及同一供应商内的模型编码均不可在逻辑删除后复用。
- 模型启用时供应商必须启用；设为默认模型时清除其余模型默认标记；删除/禁用默认模型不自动选择替代项。

## Review Focus

- 更新供应商而未传 `apiKey` 时，确认加密密文和 `credentialVersion` 完全保持不变。
- 同一 API Key 两次加密必须得到不同密文，但都能解密。
- 读取接口、错误响应和 `toString` 路径均不序列化 `apiKeyCiphertext`。
- `listAvailableModels()` 跳过归属已禁用或删除供应商的模型，而非因其中一条记录抛错。
- 连续设默认模型的更新必须使最终未删除模型中最多只有一个默认记录。

### Task 1: 数据库迁移与管理契约

**Files:**

- Create: `ai-platform-ai/src/main/resources/db/migration/V2__ai_provider_model_schema.sql`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiProviderUpsertRequest.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiModelUpsertRequest.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiProviderManagementDTO.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiModelManagementDTO.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiProvider.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiModel.java`
- Modify: `ai-platform-ai/pom.xml`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/dto/AiManagementDtoSerializationTest.java`

**Interfaces:**

- Produces validated write records and response records for service and Controller tasks; only `AiProviderManagementDTO` owns `hasApiKey`, never a key value.
- Produces Flyway tables `ai_provider` and `ai_model` matching the entity mappings.

- [ ] **Step 1: Write the failing response-serialization test**

Serialize a populated `AiProviderManagementDTO` and assert JSON includes `hasApiKey` but excludes `apiKey` and `apiKeyCiphertext`; assert a model response exposes provider metadata but no credential field.

- [ ] **Step 2: Run the DTO test to verify it fails**

Run: `mvn -pl ai-platform-ai -Dtest=AiManagementDtoSerializationTest test`

Expected: FAIL because management DTOs do not exist.

- [ ] **Step 3: Create the migration, validation records, response records, and entity accessors**

Use `varchar` for provider type and identifiers, `decimal(5,4)` for generation values, `tinyint` flags/status, `text` for encrypted credentials, and timestamp/audit/logic-delete columns. Add unique indexes `uk_ai_provider_code(provider_code)` and `uk_ai_model_provider_code(provider_id, model_code)`, plus provider/default lookup indexes. Add `spring-boot-starter-validation` to the AI module. Add Jakarta validation bounds; make the key nullable in the DTO and enforce nonblank only for create in the service. Add bean getters/setters to the existing public-field entities for lambda wrappers.

- [ ] **Step 4: Run the DTO test to verify it passes**

Run: `mvn -pl ai-platform-ai -Dtest=AiManagementDtoSerializationTest test`

Expected: PASS.

- [ ] **Step 5: Commit the contract and migration**

```bash
git add ai-platform-ai/pom.xml ai-platform-ai/src/main/resources/db/migration/V2__ai_provider_model_schema.sql ai-platform-ai/src/main/java/com/example/aichat/ai/dto ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiProvider.java ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiModel.java ai-platform-ai/src/test/java/com/example/aichat/ai/dto/AiManagementDtoSerializationTest.java
git commit -m "feat: add AI management persistence contract"
```

### Task 2: 加密凭据边界

**Files:**

- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/config/AiCredentialCipher.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/config/AiProperties.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/config/AiConfiguration.java`
- Modify: `ai-platform-boot/src/main/resources/application.yml`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/config/AiCredentialCipherTest.java`

**Interfaces:**

- Produces `String encrypt(String plaintext)` and `String decrypt(String ciphertext)` on `AiCredentialCipher`.
- Produces an `AiCredentialResolver` backed by `AiCredentialCipher::decrypt` for existing model adapters.

- [ ] **Step 1: Write failing cipher tests**

Test AES-GCM plaintext round-trip, different ciphertexts for identical input, and rejection for empty/invalid master key and tampered ciphertext. These tests must fail for deterministic encryption, plaintext persistence, and wrong-key decryption.

- [ ] **Step 2: Run the cipher tests to verify they fail**

Run: `mvn -pl ai-platform-ai -Dtest=AiCredentialCipherTest test`

Expected: FAIL because `AiCredentialCipher` does not exist.

- [ ] **Step 3: Implement AES-GCM configuration and cipher**

Add `credentialEncryptionKey` to `AiProperties`; bind `ai.credential-encryption-key` to `${AI_CREDENTIAL_ENCRYPTION_KEY:}`. Decode a Base64 256-bit key, generate a 12-byte nonce per encryption, store a versioned Base64 payload, and use `AES/GCM/NoPadding`. Reject empty inputs, absent/invalid keys and malformed ciphertext without echoing sensitive values. Wire the conditional resolver bean to cipher decryption.

- [ ] **Step 4: Run cipher tests to verify they pass**

Run: `mvn -pl ai-platform-ai -Dtest=AiCredentialCipherTest test`

Expected: PASS.

- [ ] **Step 5: Commit credential protection**

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/config ai-platform-ai/src/test/java/com/example/aichat/ai/config/AiCredentialCipherTest.java ai-platform-boot/src/main/resources/application.yml
git commit -m "feat: encrypt AI provider credentials"
```

### Task 3: 供应商 CRUD 服务

**Files:**

- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/AiProviderService.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiProviderServiceImpl.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/mapper/AiProviderMapper.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/AiProviderServiceImplTest.java`

**Interfaces:**

- Consumes `AiProviderUpsertRequest`, `AiProviderManagementDTO`, `AiCredentialCipher`.
- Produces `list()`, `getRequired(Long)`, `create(AiProviderUpsertRequest)`, `update(Long, AiProviderUpsertRequest)`, and `delete(Long)` while retaining `requireEnabled(Long)`.

- [ ] **Step 1: Write failing provider service tests**

With mocked mapper/cipher collaborators, specify: create encrypts a nonblank API Key and sets version 1; duplicate code conflicts; update without a key preserves ciphertext/version; update with a key replaces ciphertext/increments version; deletion with a dependent model fails; mapped DTO only contains `hasApiKey`.

- [ ] **Step 2: Run provider tests to verify they fail**

Run: `mvn -pl ai-platform-ai -Dtest=AiProviderServiceImplTest test`

Expected: FAIL because CRUD methods and dependencies do not exist.

- [ ] **Step 3: Implement transactional provider CRUD and mapper queries**

Inject `AiModelMapper` and cipher. Use global code checks (including logically deleted rows, matching the database unique index) and deterministic `sort`, then `id` ordering; map conflicts/not-found cases to `BusinessException`. Require a key on create, preserve blank update, encrypt replacement, and count non-deleted models before logical deletion.

- [ ] **Step 4: Run provider tests to verify they pass**

Run: `mvn -pl ai-platform-ai -Dtest=AiProviderServiceImplTest test`

Expected: PASS.

- [ ] **Step 5: Commit provider management service**

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/service ai-platform-ai/src/main/java/com/example/aichat/ai/mapper/AiProviderMapper.java ai-platform-ai/src/test/java/com/example/aichat/ai/service/AiProviderServiceImplTest.java
git commit -m "feat: add AI provider management service"
```

### Task 4: 模型 CRUD、默认规则与可用模型查询

**Files:**

- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/AiModelService.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/mapper/AiModelMapper.java`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/AiModelServiceImplTest.java`

**Interfaces:**

- Consumes model write/response records and provider management lookup.
- Produces `listManagement()`, `getManagementRequired(Long)`, `create(AiModelUpsertRequest)`, `update(Long, AiModelUpsertRequest)`, and `delete(Long)` while preserving runtime query methods.

- [ ] **Step 1: Write failing model service tests**

Specify creation rejects an absent provider and an enabled model under a disabled provider; duplicate `(providerId, modelCode)` conflicts; selecting default clears existing default; deletion is logical; and `listAvailableModels()` excludes models whose provider is disabled instead of throwing.

- [ ] **Step 2: Run model tests to verify they fail**

Run: `mvn -pl ai-platform-ai -Dtest=AiModelServiceImplTest test`

Expected: FAIL because management methods and corrected availability filtering do not exist.

- [ ] **Step 3: Implement transactional model CRUD**

Use provider lookup for referential validation, lambda wrappers for uniqueness/order, and a transactional bulk update to clear other default flags before persisting a default. Fetch enabled models and retain only records with an enabled provider. Map management DTOs without credentials.

- [ ] **Step 4: Run model tests to verify they pass**

Run: `mvn -pl ai-platform-ai -Dtest=AiModelServiceImplTest test`

Expected: PASS.

- [ ] **Step 5: Commit model management service**

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/service/AiModelService.java ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java ai-platform-ai/src/main/java/com/example/aichat/ai/mapper/AiModelMapper.java ai-platform-ai/src/test/java/com/example/aichat/ai/service/AiModelServiceImplTest.java
git commit -m "feat: add AI model management service"
```

### Task 5: Admin REST Controllers and permission data

**Files:**

- Modify: `ai-platform-admin/pom.xml`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AiProviderController.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AiModelController.java`
- Modify: `ai-platform-ai/src/main/resources/db/migration/V2__ai_provider_model_schema.sql`
- Test: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AiManagementControllerTest.java`

**Interfaces:**

- Consumes CRUD methods from `AiProviderService` and `AiModelService`.
- Produces permission-protected endpoints under `/api/admin/ai/providers` and `/api/admin/ai/models`.

- [ ] **Step 1: Write failing MVC/annotation tests**

Assert each Controller maps list, detail, POST, PUT, and DELETE to the agreed paths, wraps successful output in `ApiResponse.success`, validates invalid request bodies, and declares the exact `ai:provider:*` or `ai:model:*` `@SaCheckPermission`. Assert a provider read response has no API Key fields.

- [ ] **Step 2: Run controller tests to verify they fail**

Run: `mvn -pl ai-platform-admin -am -Dtest=AiManagementControllerTest test`

Expected: FAIL because admin lacks the AI dependency and Controllers.

- [ ] **Step 3: Add module dependency, Controllers, and permission rows**

Add `ai-platform-ai`, `spring-boot-starter-web`, `spring-boot-starter-validation`, and `sa-token-spring-boot3-starter` as admin dependencies. Follow the existing system Controller style with `@Valid`, `ApiResponse`, and one `@SaCheckPermission` per method. Insert the eight permission codes (`ai:provider:read/create/update/delete`, `ai:model:read/create/update/delete`) idempotently in V2 as button permissions (`permission_type=3`); do not assign them to a role automatically.

- [ ] **Step 4: Run controller tests to verify they pass**

Run: `mvn -pl ai-platform-admin -am -Dtest=AiManagementControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit admin API**

```bash
git add ai-platform-admin/pom.xml ai-platform-admin/src/main ai-platform-admin/src/test ai-platform-ai/src/main/resources/db/migration/V2__ai_provider_model_schema.sql
git commit -m "feat: expose AI management admin APIs"
```

### Task 6: 全工程验证与收尾

**Files:**

- Modify: `docs/superpowers/specs/2026-10-13-ai-provider-model-crud-design.md` only for an agreed implementation correction.
- Test: all module test suites.

**Interfaces:**

- Consumes all prior tasks.
- Produces verified repository state with no credential-leak regression.

- [ ] **Step 1: Run targeted tests**

Run: `mvn -pl ai-platform-ai,ai-platform-admin -am test`

Expected: PASS with all new unit and controller tests green.

- [ ] **Step 2: Run root verification**

Run: `mvn verify`

Expected: PASS for all modules; report any pre-existing failure by module.

- [ ] **Step 3: Inspect final diff for secrets and migration correctness**

Run: `git diff --check` and `git diff --cached --check` as applicable; review response DTOs and logging to confirm no API Key, ciphertext, or configured master key is present.

- [ ] **Step 4: Commit verification-only corrections if needed**

```bash
git add <only-files-corrected-by-verification>
git commit -m "test: verify AI management CRUD"
```
