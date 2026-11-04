# ChatModelController Embedding 模型查询接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `ChatModelController` 中提供 `/api/chat/models/embedding`，返回启用且所属 Provider 启用的 Embedding 模型。

**Architecture:** `ChatModelController` 仅负责 HTTP 和 `ApiResponse` 封装；`AiModelQueryService` 暴露用户侧模型目录查询契约；`AiModelServiceImpl` 通过 `AiModelMapper` 查询 Embedding 模型，再用 `AiProviderService` 过滤停用或不可用 Provider，并映射到专用 DTO。现有聊天模型查询路径和 DTO 不变。

**Tech Stack:** Java 17、Spring Web MVC、MyBatis-Plus、JUnit 5、Mockito、AssertJ、Spring MockMvc、Maven。

---

## 文件结构

- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiEmbeddingModelDTO.java` — 用户侧 Embedding 模型公开响应字段。
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/api/AiModelQueryService.java` — 增加 Embedding 查询契约。
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java` — 实现查询、过滤、排序和 DTO 映射。
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/controller/ChatModelController.java` — 增加 `GET /embedding`。
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java` — 覆盖 Embedding 列表行为，同时保留聊天模型隔离测试。
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/controller/ChatModelControllerTest.java` — 覆盖 HTTP 路径和统一响应结构。

不修改数据库、POM、管理控制器、`AiModelDTO` 或知识库调用流程。

## Task 1: Add the query contract and response DTO

**Files:**
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiEmbeddingModelDTO.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/api/AiModelQueryService.java:3-9`

- [ ] **Step 1: Add the public DTO required by the query contract**

Create `AiEmbeddingModelDTO.java` with exactly the fields required by the design:

```java
package com.example.aichat.ai.dto.response;

public record AiEmbeddingModelDTO(Long id, Long providerId, String providerName, String modelCode,
                                  String modelName, Integer embeddingDimension) { }
```

- [ ] **Step 2: Add the query method without changing the existing chat method**

Import `AiEmbeddingModelDTO` and append the new method to `AiModelQueryService`:

```java
import com.example.aichat.ai.dto.response.AiEmbeddingModelDTO;

// existing method remains
List<AiModelDTO> listAvailableModels();

List<AiEmbeddingModelDTO> listAvailableEmbeddingModels();
```

- [ ] **Step 3: Compile to expose the missing implementation**

Run:

```bash
mvn -pl ai-platform-ai -DskipTests compile
```

Expected: FAIL because `AiModelServiceImpl` implements `AiModelQueryService` but does not yet implement `listAvailableEmbeddingModels()`.

- [ ] **Step 4: Confirm the DTO and interface contain no unrelated fields or methods**

Check that the DTO contains only `id`, `providerId`, `providerName`, `modelCode`, `modelName`, and `embeddingDimension`, and that `listAvailableModels()` remains unchanged. The behavior test and implementation are added together in Task 2 so no failing test is committed by itself.

- [ ] **Step 5: Commit the contract and DTO after Task 2 makes the module buildable**

After Task 2 passes its focused test, stage only these two contract files together with the Task 2 implementation files and create one feature commit:

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/api/AiModelQueryService.java \
        ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiEmbeddingModelDTO.java \
        ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java \
        ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java
git commit -m "feat: query available embedding models"
```

## Task 2: Implement available Embedding model lookup

**Files:**
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java:104-110,213-244`
- Test: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java`

- [ ] **Step 1: Add a failing service test for filtering, mapping, provider status, and order**

Add this test and import `AiEmbeddingModelDTO` in `AiModelServiceImplTest`:

```java
@Test
void availableEmbeddingModelsFilterAndMapPublicFields() {
    AiModel first = model(2L, "EMBEDDING");
    first.providerId = 1L;
    first.modelCode = "text-embedding-3-small";
    first.modelName = "Text Embedding 3 Small";
    first.embeddingDimension = 1536;
    first.sort = 2;

    AiModel second = model(1L, "EMBEDDING");
    second.providerId = 1L;
    second.modelCode = "text-embedding-ada-002";
    second.modelName = "Text Embedding Ada 002";
    second.embeddingDimension = 1536;
    second.sort = 1;

    AiModel chat = model(3L, "CHAT");
    chat.sort = 0;

    AiModel disabled = model(4L, "EMBEDDING");
    disabled.status = 0;

    AiModel disabledProviderModel = model(5L, "EMBEDDING");
    disabledProviderModel.providerId = 2L;
    disabledProviderModel.embeddingDimension = 768;

    AiProvider disabledProvider = enabledProvider();
    disabledProvider.id = 2L;
    disabledProvider.status = 0;

    when(modelMapper.selectList(any()))
            .thenReturn(List.of(second, first, chat, disabled, disabledProviderModel));
    when(providerService.getRequired(1L)).thenReturn(enabledProvider());
    when(providerService.getRequired(2L)).thenReturn(disabledProvider);

    List<AiEmbeddingModelDTO> result = service().listAvailableEmbeddingModels();

    assertThat(result).extracting(AiEmbeddingModelDTO::id).containsExactly(1L, 2L);
    assertThat(result.get(1)).satisfies(model -> {
        assertThat(model.providerId()).isEqualTo(1L);
        assertThat(model.providerName()).isEqualTo("OpenAI");
        assertThat(model.modelCode()).isEqualTo("text-embedding-3-small");
        assertThat(model.modelName()).isEqualTo("Text Embedding 3 Small");
        assertThat(model.embeddingDimension()).isEqualTo(1536);
    });
}
```

Keep the existing `availableChatModelsExcludeEmbeddingModels` test to guard the unchanged chat contract. The mocked mapper list is intentionally out of order and contains Chat, disabled-model, and disabled-Provider records, so the test verifies both the Java-level safety filters and the `sort`/ID ordering expected from the mapper query. Add this provider lookup failure case and import `ErrorCode`:

```java
@Test
void excludesEmbeddingModelWhenProviderLookupFails() {
    AiModel embedding = model(2L, "EMBEDDING");
    embedding.embeddingDimension = 1536;
    when(modelMapper.selectList(any())).thenReturn(List.of(embedding));
    when(providerService.getRequired(1L))
            .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    assertThat(service().listAvailableEmbeddingModels()).isEmpty();
}
```

- [ ] **Step 2: Run the new test and verify it fails**

Run:

```bash
mvn -pl ai-platform-ai -Dtest=AiModelServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `listAvailableEmbeddingModels()` and the Embedding mapping do not yet exist.

- [ ] **Step 3: Implement the minimal query method**

Add this method next to `listAvailableModels()`:

```java
@Override
public List<AiEmbeddingModelDTO> listAvailableEmbeddingModels() {
    return modelMapper.selectList(Wrappers.<AiModel>query()
                    .eq("status", 1)
                    .eq("model_type", AiModelType.EMBEDDING.name())
                    .orderByAsc("sort", "id"))
            .stream()
            .filter(model -> Integer.valueOf(1).equals(model.status))
            .filter(model -> AiModelType.EMBEDDING.name().equalsIgnoreCase(model.modelType))
            .flatMap(model -> availableEmbeddingDto(model).stream())
            .toList();
}
```

Add a helper beside `availableDto` that follows the existing public-list behavior: provider lookup failures and disabled Providers produce an empty Optional, while an enabled Provider is mapped to the public DTO.

```java
private java.util.Optional<AiEmbeddingModelDTO> availableEmbeddingDto(AiModel model) {
    try {
        AiProvider provider = providerService.getRequired(model.providerId);
        return Integer.valueOf(1).equals(provider.getStatus())
                ? java.util.Optional.of(new AiEmbeddingModelDTO(
                        model.id, model.providerId, provider.providerName, model.modelCode,
                        model.modelName, model.embeddingDimension))
                : java.util.Optional.empty();
    } catch (BusinessException ignored) {
        return java.util.Optional.empty();
    }
}
```

Use the existing `BusinessException` import; do not expose Provider credentials or add new validation outside the existing model/database invariants.

- [ ] **Step 4: Run the service tests and verify they pass**

Run:

```bash
mvn -pl ai-platform-ai -Dtest=AiModelServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS, including both the new Embedding test and the existing Chat-only test.

- [ ] **Step 5: Commit the service implementation with the Task 1 contract files**

The contract and service are one compilable change, so use the commit command from Task 1 Step 5 after this test passes.

## Task 3: Expose the endpoint from ChatModelController

**Files:**
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/controller/ChatModelController.java:3-24`
- Test: `ai-platform-chat/src/test/java/com/example/aichat/chat/controller/ChatModelControllerTest.java`

- [ ] **Step 1: Write the failing MockMvc test**

Create `ChatModelControllerTest.java` using the same standalone MockMvc pattern as `FileControllerTest`:

```java
package com.example.aichat.chat.controller;

import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.dto.response.AiEmbeddingModelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatModelControllerTest {
    @Mock
    private AiModelQueryService modelQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatModelController(modelQueryService)).build();
    }

    @Test
    void listsAvailableEmbeddingModels() throws Exception {
        when(modelQueryService.listAvailableEmbeddingModels()).thenReturn(List.of(
                new AiEmbeddingModelDTO(2L, 1L, "OpenAI", "text-embedding-3-small",
                        "Text Embedding 3 Small", 1536)));

        mockMvc.perform(get("/api/chat/models/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].providerName").value("OpenAI"))
                .andExpect(jsonPath("$.data[0].modelCode").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.data[0].embeddingDimension").value(1536));

        verify(modelQueryService).listAvailableEmbeddingModels();
    }
}
```

- [ ] **Step 2: Run the controller test and verify it fails**

Run:

```bash
mvn -pl ai-platform-chat -am -Dtest=ChatModelControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or test failure because the controller has no `/embedding` mapping and the query interface may not yet be implemented by the compiled service.

- [ ] **Step 3: Add the controller mapping**

Import `AiEmbeddingModelDTO` and add this method below the existing `list()` method:

```java
@GetMapping("/embedding")
public ApiResponse<List<AiEmbeddingModelDTO>> listEmbeddingModels() {
    return ApiResponse.success(modelQueryService.listAvailableEmbeddingModels());
}
```

Do not add `@SaCheckPermission`; authentication remains supplied by the existing `/api/**` interceptor in the application configuration.

- [ ] **Step 4: Run the controller test and verify it passes**

Run:

```bash
mvn -pl ai-platform-chat -am -Dtest=ChatModelControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: PASS with HTTP 200, `success=true`, the expected DTO fields, and one invocation of the new query method.

- [ ] **Step 5: Commit the endpoint and test**

```bash
git add ai-platform-chat/src/main/java/com/example/aichat/chat/controller/ChatModelController.java \
        ai-platform-chat/src/test/java/com/example/aichat/chat/controller/ChatModelControllerTest.java
git commit -m "feat: expose embedding model query endpoint"
```

## Task 4: Run full verification and inspect the final diff

**Files:**
- Verify all files from Tasks 1-3.

- [ ] **Step 1: Run all AI and Chat module tests**

```bash
mvn -pl ai-platform-chat -am test
```

Expected: BUILD SUCCESS with all existing and newly added tests passing.

- [ ] **Step 2: Check formatting and accidental changes**

```bash
git diff master...HEAD --check
git status --short
git diff master...HEAD --stat
```

Expected: no whitespace errors; only the planned DTO, query contract/service, controller, and test files are included in the feature commits. The pre-existing untracked `.DS_Store` remains untracked and is not staged.

- [ ] **Step 3: Confirm endpoint behavior against the design**

Verify the final implementation has all of the following:

- `GET /api/chat/models/embedding` is mapped in `ChatModelController`.
- The response type is `ApiResponse<List<AiEmbeddingModelDTO>>`.
- The service query filters `EMBEDDING`, model status `1`, and enabled Provider status `1`.
- Results are ordered by model `sort`, then model ID.
- `embeddingDimension` is returned.
- `GET /api/chat/models` still uses `listAvailableModels()` and returns only Chat models.
- No database migration, anonymous whitelist, management endpoint, or permission annotation was added.

- [ ] **Step 4: Commit any verification-only correction as a new commit**

If verification reveals a real implementation issue, fix only the affected planned file, rerun the focused test and full module test, then create a new commit rather than amending an earlier commit. Stage the four AI feature files explicitly:

```bash
git add ai-platform-ai/src/main/java/com/example/aichat/ai/api/AiModelQueryService.java \
        ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiEmbeddingModelDTO.java \
        ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java \
        ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java
git commit -m "fix: correct embedding model query behavior"
```
