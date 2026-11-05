# 知识文档正文读取接口 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 TEXT 类型知识文档提供带权限校验的正文读取接口，并以统一 `ApiResponse` 返回文档 ID、标题和原始正文。

**Architecture:** 在现有 `KnowledgeBaseService` 增加 `documentContent` 业务方法。该方法复用 `KnowledgeBaseServiceImpl.requireDocument` 完成知识库与文档归属校验，拒绝非 TEXT 文档后直接返回 `rawContent`；Controller 只负责当前用户解析、路由绑定和统一响应包装。索引流程继续使用现有 `KnowledgeDocumentContentReader`，不扩大本接口的文件读取范围。

**Tech Stack:** Java 17 records、Spring Boot MVC、Mockito/JUnit 5、Spring MockMvc、Maven 多模块构建。

---

## 文件变更地图

- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java` — 增加非 TEXT 正文不支持错误码。
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/vo/KnowledgeDocumentContentVO.java` — 定义正文接口的响应数据结构。
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/KnowledgeBaseService.java` — 暴露正文读取业务方法。
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/impl/KnowledgeBaseServiceImpl.java` — 实现所有权、来源类型和正文校验。
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/controller/KnowledgeBaseController.java` — 注册 GET 路由并返回 `ApiResponse`。
- Modify: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/service/KnowledgeBaseServiceImplTest.java` — 覆盖正文读取服务行为。
- Create: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/controller/KnowledgeBaseControllerTest.java` — 覆盖接口路径、响应结构和用户 ID 传递。

---

### Task 1: 添加正文读取 Service 契约测试

**Files:**
- Modify: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/service/KnowledgeBaseServiceImplTest.java`

- [ ] **Step 1: 扩展测试工具方法，使测试可以构造不同来源和正文的文档**

在现有 `KnowledgeBaseServiceImplTest` 中，将固定的 `document(Long id, Long baseId)` helper 改为委托到带来源、文件 ID 和正文参数的 helper，并保留现有调用行为：

```java
private KnowledgeDocumentRecord document(Long id, Long baseId) {
    return document(id, baseId, KnowledgeDocumentSourceType.TEXT, null, "content");
}

private KnowledgeDocumentRecord document(Long id, Long baseId,
                                         KnowledgeDocumentSourceType sourceType,
                                         Long fileId,
                                         String rawContent) {
    return new KnowledgeDocumentRecord(id, baseId, sourceType, fileId,
            "title", rawContent, null, KnowledgeDocumentStatus.READY, 1L, 1L,
            1, null, null, false, LocalDateTime.now(), LocalDateTime.now());
}
```

- [ ] **Step 2: 写成功读取 TEXT 文档的测试**

追加测试，使用包含换行和前后空格的正文验证 Service 不做规范化：

```java
@Test
void returnsOriginalTextDocumentContent() {
    when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
    when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
            21L, 12L, KnowledgeDocumentSourceType.TEXT, null, " 第一条\n\n第二条 " )));

    KnowledgeDocumentContentVO result = service.documentContent(100L, 12L, 21L);

    assertEquals(21L, result.documentId());
    assertEquals("title", result.title());
    assertEquals(" 第一条\n\n第二条 ", result.content());
}
```

为测试文件增加：

```java
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
```

- [ ] **Step 3: 写路径知识库不拥有文档的测试**

追加测试，验证现有 `requireDocument` 的归属保护也适用于正文接口：

```java
@Test
void rejectsContentWhenPathKnowledgeBaseDoesNotOwnDocument() {
    when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
    when(documentRepository.findById(21L)).thenReturn(Optional.of(document(21L, 11L)));

    BusinessException exception = assertThrows(BusinessException.class,
            () -> service.documentContent(100L, 12L, 21L));

    assertEquals("KNOWLEDGE_DOCUMENT_NOT_FOUND", exception.errorCode().code());
}
```

- [ ] **Step 4: 写非 TEXT 文档返回新错误码的测试**

追加测试，确保 Service 不尝试读取 FILE 文档内容：

```java
@Test
void rejectsContentForNonTextDocument() {
    when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
    when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
            21L, 12L, KnowledgeDocumentSourceType.FILE, 31L, null)));

    BusinessException exception = assertThrows(BusinessException.class,
            () -> service.documentContent(100L, 12L, 21L));

    assertEquals("KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED", exception.errorCode().code());
}
```

- [ ] **Step 5: 写空 TEXT 正文返回解析错误的测试**

追加测试，覆盖数据库中异常为空的旧数据：

```java
@Test
void rejectsEmptyTextDocumentContent() {
    when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
    when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
            21L, 12L, KnowledgeDocumentSourceType.TEXT, null, "  ")));

    BusinessException exception = assertThrows(BusinessException.class,
            () -> service.documentContent(100L, 12L, 21L));

    assertEquals("KNOWLEDGE_DOCUMENT_PARSE_FAILED", exception.errorCode().code());
}
```

- [ ] **Step 6: 运行 Service 测试，确认测试先因缺少新方法或实现而失败**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dtest=KnowledgeBaseServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL during compilation or assertion because `documentContent`、响应 VO 和实现尚未添加；不得因为已有的删除文档测试失败。

---

### Task 2: 实现正文读取 Service 和错误码

**Files:**
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/vo/KnowledgeDocumentContentVO.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/KnowledgeBaseService.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/impl/KnowledgeBaseServiceImpl.java`

- [ ] **Step 1: 在公共错误码中加入非 TEXT 正文错误**

在 `KNOWLEDGE_DOCUMENT_NOT_FOUND` 后增加：

```java
KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED(
        HttpStatus.UNPROCESSABLE_ENTITY,
        "KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED",
        "Knowledge document content is not supported"),
```

保持现有错误码顺序和 `GlobalExceptionHandler` 不变。

- [ ] **Step 2: 创建正文响应 record**

创建 `KnowledgeDocumentContentVO.java`：

```java
package com.example.aichat.knowledge.vo;

public record KnowledgeDocumentContentVO(Long documentId, String title, String content) {
}
```

- [ ] **Step 3: 扩展 Service 接口**

在 `KnowledgeBaseService` 的文档方法附近增加：

```java
KnowledgeDocumentContentVO documentContent(Long userId, Long knowledgeBaseId, Long documentId);
```

同时增加对应 import：

```java
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
```

- [ ] **Step 4: 实现最小正文读取逻辑**

在 `KnowledgeBaseServiceImpl` 中增加 `KnowledgeDocumentContentVO` import，并在 `documentPage` 或 `addText` 前增加：

```java
@Override
public KnowledgeDocumentContentVO documentContent(Long userId, Long knowledgeBaseId, Long documentId) {
    KnowledgeDocumentRecord document = requireDocument(userId, knowledgeBaseId, documentId);
    if (document.sourceType() != KnowledgeDocumentSourceType.TEXT) {
        throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED);
    }
    if (!StringUtils.hasText(document.rawContent())) {
        throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                "Knowledge document content is empty");
    }
    return new KnowledgeDocumentContentVO(document.id(), document.title(), document.rawContent());
}
```

该实现必须直接返回 `rawContent`，不得调用 `KnowledgeTextNormalizer`、`TextChunker` 或 `KnowledgeDocumentContentReader`，以保留接口约定的原始正文和仅 TEXT 范围。

- [ ] **Step 5: 运行 Service 测试确认通过**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dtest=KnowledgeBaseServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `KnowledgeBaseServiceImplTest` 全部 PASS。

---

### Task 3: 添加 Controller 契约测试

**Files:**
- Create: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/controller/KnowledgeBaseControllerTest.java`

- [ ] **Step 1: 创建 MockMvc 测试夹具**

使用现有 Controller 测试风格，创建以下测试类和字段：

```java
package com.example.aichat.knowledge.controller;

import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.knowledge.service.KnowledgeBaseService;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {
    @Mock
    private KnowledgeBaseService service;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new KnowledgeBaseController(service, currentUserProvider)).build();
        when(currentUserProvider.findCurrentUser()).thenReturn(
                Optional.of(new CurrentUser(100L, "alice")));
    }
}
```

- [ ] **Step 2: 写成功响应的失败测试**

在测试类中追加：

```java
@Test
void readsTextDocumentContent() throws Exception {
    when(service.documentContent(100L, 12L, 21L)).thenReturn(
            new KnowledgeDocumentContentVO(21L, "请假制度", "第一条……"));

    mockMvc.perform(get("/api/knowledge-bases/12/documents/21/content"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("OK"))
            .andExpect(jsonPath("$.data.documentId").value(21))
            .andExpect(jsonPath("$.data.title").value("请假制度"))
            .andExpect(jsonPath("$.data.content").value("第一条……"));

    verify(service).documentContent(100L, 12L, 21L);
}
```

- [ ] **Step 3: 运行 Controller 测试确认新路由尚未实现**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dtest=KnowledgeBaseControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `KnowledgeBaseController` does not yet expose `/documents/{documentId}/content`.

---

### Task 4: 实现 Controller 路由并验证接口

**Files:**
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/controller/KnowledgeBaseController.java`

- [ ] **Step 1: 增加正文 VO import**

在 Controller import 区域增加：

```java
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
```

- [ ] **Step 2: 增加 GET 路由**

在文档列表、创建或删除接口附近增加：

```java
@GetMapping("/{knowledgeBaseId}/documents/{documentId}/content")
public ApiResponse<KnowledgeDocumentContentVO> documentContent(
        @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
        @PathVariable("documentId") Long documentId) {
    return ApiResponse.success(service.documentContent(
            currentUserProvider.requireCurrentUser().id(), knowledgeBaseId, documentId));
}
```

不要在 Controller 中直接访问 Repository、判断 `sourceType` 或读取 `rawContent`；这些行为必须留在 Service。

- [ ] **Step 3: 运行 Controller 测试确认通过**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dtest=KnowledgeBaseControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `KnowledgeBaseControllerTest` PASS。

- [ ] **Step 4: 运行知识库模块完整测试**

Run:

```bash
mvn -pl ai-platform-knowledge -am -DskipTests=false test
```

Expected: `ai-platform-knowledge` 及其依赖模块测试 PASS；若 Docker 不可用，Testcontainers 测试按项目现有 `disabledWithoutDocker = true` 规则跳过，其余测试仍 PASS。

- [ ] **Step 5: 检查最终改动范围**

Run:

```bash
git diff --check && git status --short
```

Expected: 无 whitespace error；改动仅包含本计划列出的公共错误码、知识库 Service/Controller/VO、对应测试，以及本次规格/计划文件。不要将已有的 `.DS_Store` 加入提交。

---

## 计划自检

- Spec coverage: 已覆盖统一 `ApiResponse`、`documentId/title/content`、原样 `rawContent`、当前用户和知识库/文档归属校验、非 TEXT 新错误码、空正文错误、索引流程不变和 Service/Controller 测试。
- Placeholder scan: 未使用 TBD、TODO、"适当处理" 或未定义的后续步骤；每个代码变更步骤均给出目标签名或完整代码片段。
- Type consistency: `KnowledgeDocumentContentVO`、`KnowledgeBaseService.documentContent`、`KnowledgeBaseController.documentContent` 的字段和参数顺序在所有任务中保持一致。
