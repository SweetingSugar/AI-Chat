# 管理员查看知识库及文档 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 admin 模块增加管理员分页查看全部未删除知识库、对应文档及文本文档内容的只读接口，同时保持知识库 PostgreSQL 数据源和现有权限边界。

**Architecture:** `ai-platform-knowledge` 提供独立的 `KnowledgeBaseAdminService`，负责 PostgreSQL 查询、分页和跨资源校验；`ai-platform-admin` 增加 `/api/admin/knowledge-bases/**` 控制器，只负责 HTTP 参数和响应包装。管理员接口使用 `knowledge:read` 权限，普通用户的 `/api/knowledge-bases/**` 契约不变。

**Tech Stack:** Java 17、Spring Boot 3.5、MyBatis、PostgreSQL/pgvector、Sa-Token、JUnit 5、Mockito、MockMvc、Testcontainers、Flyway。

---

## 文件变更总览

### `ai-platform-knowledge`

- Modify: `src/main/java/com/example/aichat/knowledge/mapper/KnowledgeBaseMapper.java` — 增加不按用户过滤的分页、计数和未删除单条查询。
- Modify: `src/main/resources/mapper/postgresql/KnowledgeBaseMapper.xml` — 实现上述 PostgreSQL SQL，固定 `deleted = false` 和 `update_time desc, id desc` 排序。
- Modify: `src/main/java/com/example/aichat/knowledge/repository/KnowledgeBaseRepository.java` — 暴露全量只读查询给服务层。
- Create: `src/main/java/com/example/aichat/knowledge/vo/KnowledgeBaseAdminVO.java` — 管理员知识库响应，额外返回 `userId`。
- Create: `src/main/java/com/example/aichat/knowledge/service/KnowledgeBaseAdminService.java` — 管理员只读服务接口。
- Create: `src/main/java/com/example/aichat/knowledge/service/impl/KnowledgeBaseAdminServiceImpl.java` — 分页、资源归属和文本内容校验实现。
- Modify: `src/test/java/com/example/aichat/knowledge/mapper/KnowledgeMapperXmlTest.java` — 验证全量查询排除逻辑删除记录。
- Create: `src/test/java/com/example/aichat/knowledge/service/KnowledgeBaseAdminServiceImplTest.java` — 验证管理员服务的分页和资源安全边界。

### `ai-platform-admin`

- Modify: `pom.xml` — 增加对 `ai-platform-knowledge` 的直接依赖。
- Create: `src/main/java/com/example/aichat/admin/controller/AdminKnowledgeBaseController.java` — 管理员知识库、文档和文本内容端点。
- Create: `src/test/java/com/example/aichat/admin/controller/AdminKnowledgeBaseControllerTest.java` — 验证三个端点的路径、参数、响应和服务调用。
- Create: `src/main/resources/db/migration/V12__admin_knowledge_permission.sql` — 注册 `knowledge:read` 权限，使用全局唯一版本和排序值。

### 文档

- Modify: `docs/frontend-api-integration.md` — 增加管理员知识库 API 契约。
- Modify: `docs/backend-development-guide.md` — 更新 admin 模块能力和接口定位。

---

### Task 1: Add PostgreSQL all-knowledge-base queries

**Files:**
- Test: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/mapper/KnowledgeMapperXmlTest.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeBaseMapper.java`
- Modify: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeBaseMapper.xml`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeBaseRepository.java`

- [ ] **Step 1: Write the failing mapper integration test**

Append this test to `KnowledgeMapperXmlTest`:

```java
@Test
void pagesOnlyNonDeletedKnowledgeBases() {
    long initialCount = baseMapper.countAll();
    Long visibleId = baseMapper.insert(210L, "管理员可见知识库", null, 30L, 1);
    Long deletedId = baseMapper.insert(211L, "管理员不可见知识库", null, 30L, 1);
    baseMapper.markDeleted(211L, deletedId);

    assertThat(baseMapper.countAll()).isEqualTo(initialCount + 1);
    assertThat(baseMapper.findNotDeletedById(visibleId).id()).isEqualTo(visibleId);
    assertThat(baseMapper.findNotDeletedById(deletedId)).isNull();
    assertThat(baseMapper.findPageAll(100, 0))
            .extracting(KnowledgeBaseRecord::id)
            .contains(visibleId)
            .doesNotContain(deletedId);
}
```

Add the missing import:

```java
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
```

- [ ] **Step 2: Run the test and verify it fails for the missing mapper contract**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeMapperXmlTest test
```

Expected: compilation fails because `KnowledgeBaseMapper` does not yet declare `findNotDeletedById` or `findPageAll`.

- [ ] **Step 3: Add the mapper methods**

Add these methods to `KnowledgeBaseMapper`:

```java
List<KnowledgeBaseRecord> findPageAll(@Param("limit") int limit,
                                      @Param("offset") int offset);

long countAll();

KnowledgeBaseRecord findNotDeletedById(@Param("id") Long id);
```

Add these XML statements to `KnowledgeBaseMapper.xml`, using the existing `knowledgeBaseResultMap` and `baseColumns`:

```xml
<select id="findPageAll" resultMap="knowledgeBaseResultMap">
    select <include refid="baseColumns"/>
    from knowledge_base
    where deleted = false
    order by update_time desc, id desc
    limit #{limit} offset #{offset}
</select>

<select id="countAll" resultType="long">
    select count(*)
    from knowledge_base
    where deleted = false
</select>

<select id="findNotDeletedById" resultMap="knowledgeBaseResultMap">
    select <include refid="baseColumns"/>
    from knowledge_base
    where id = #{id} and deleted = false
</select>
```

Add these repository methods to `KnowledgeBaseRepository`:

```java
public List<KnowledgeBaseRecord> findPageAll(int page, int size) {
    int offset = Math.max(0, page - 1) * size;
    return mapper.findPageAll(size, offset);
}

public long countAll() {
    return mapper.countAll();
}

public Optional<KnowledgeBaseRecord> findNotDeletedById(Long id) {
    return Optional.ofNullable(mapper.findNotDeletedById(id));
}
```

- [ ] **Step 4: Run the mapper test and verify it passes**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeMapperXmlTest test
```

Expected: `KnowledgeMapperXmlTest` passes when Docker is available; when Docker is unavailable, the existing Testcontainers configuration skips the test without a compilation or XML parsing failure.

---

### Task 2: Implement the administrator knowledge service with tests first

**Files:**
- Test: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/service/KnowledgeBaseAdminServiceImplTest.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/vo/KnowledgeBaseAdminVO.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/KnowledgeBaseAdminService.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/impl/KnowledgeBaseAdminServiceImpl.java`

- [ ] **Step 1: Write the service tests**

Create `KnowledgeBaseAdminServiceImplTest` with Mockito mocks for `KnowledgeBaseRepository` and `KnowledgeDocumentRepository`. Use these test methods and fixtures:

```java
@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAdminServiceImplTest {
    @Mock
    private KnowledgeBaseRepository baseRepository;
    @Mock
    private KnowledgeDocumentRepository documentRepository;

    private KnowledgeBaseAdminService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseAdminServiceImpl(baseRepository, documentRepository);
    }

    @Test
    void pagesAllBasesWithNormalizedPagination() {
        KnowledgeBaseRecord record = base(12L, 100L, false);
        when(baseRepository.countAll()).thenReturn(1L);
        when(baseRepository.findPageAll(1, 100)).thenReturn(List.of(record));

        PageResult<KnowledgeBaseAdminVO> result = service.page(0, 101);

        assertThat(result.records()).singleElement().satisfies(base -> {
            assertThat(base.id()).isEqualTo(12L);
            assertThat(base.userId()).isEqualTo(100L);
            assertThat(base.status()).isEqualTo(KnowledgeBaseStatus.ACTIVE);
        });
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.pages()).isEqualTo(1);
        verify(baseRepository).findPageAll(1, 100);
    }

    @Test
    void rejectsDocumentPageWhenBaseIsMissingOrDeleted() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.documentPage(12L, 1, 20));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_BASE_NOT_FOUND");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void rejectsDocumentContentFromAnotherKnowledgeBase() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 11L, KnowledgeDocumentSourceType.TEXT, "内容")));

        Throwable thrown = catchThrowable(() -> service.documentContent(12L, 21L));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_DOCUMENT_NOT_FOUND");
    }

    @Test
    void returnsTextContent() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 12L, KnowledgeDocumentSourceType.TEXT, "原始内容")));

        KnowledgeDocumentContentVO result = service.documentContent(12L, 21L);

        assertThat(result.documentId()).isEqualTo(21L);
        assertThat(result.title()).isEqualTo("文档");
        assertThat(result.content()).isEqualTo("原始内容");
    }

    @Test
    void rejectsFileContent() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 12L, KnowledgeDocumentSourceType.FILE, null)));

        Throwable thrown = catchThrowable(() -> service.documentContent(12L, 21L));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED");
    }

    private KnowledgeBaseRecord base(Long id, Long userId, boolean deleted) {
        return new KnowledgeBaseRecord(id, userId, "知识库", null, 30L, 1,
                null, null, null, 1L, KnowledgeBaseStatus.ACTIVE, deleted,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeDocumentRecord document(Long id, Long baseId,
                                             KnowledgeDocumentSourceType sourceType,
                                             String rawContent) {
        return new KnowledgeDocumentRecord(id, baseId, sourceType,
                sourceType == KnowledgeDocumentSourceType.FILE ? 31L : null,
                "文档", rawContent, null, KnowledgeDocumentStatus.READY,
                1L, 1L, 1, null, null, false,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
```

The test imports must include `PageResult`, `BusinessException`, the knowledge enums/models/repositories/service/VOs, JUnit 5, Mockito, `LocalDateTime`, `List`, and `Optional`; use AssertJ’s `assertThat`, `catchThrowable`, `verify`, `verifyNoInteractions`, and `when`.

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeBaseAdminServiceImplTest test
```

Expected: compilation fails because the administrator VO, service interface, and implementation do not exist.

- [ ] **Step 3: Add the administrator response record and service contract**

Create `KnowledgeBaseAdminVO`:

```java
package com.example.aichat.knowledge.vo;

import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;

import java.time.LocalDateTime;

public record KnowledgeBaseAdminVO(
        Long id,
        Long userId,
        String name,
        String description,
        Long embeddingModelId,
        Integer embeddingModelVersion,
        Long pendingEmbeddingModelId,
        Integer pendingEmbeddingModelVersion,
        Long activeIndexVersion,
        boolean reindexing,
        KnowledgeBaseStatus status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
```

Create `KnowledgeBaseAdminService`:

```java
package com.example.aichat.knowledge.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;

public interface KnowledgeBaseAdminService {
    PageResult<KnowledgeBaseAdminVO> page(int page, int size);

    PageResult<KnowledgeDocumentVO> documentPage(Long knowledgeBaseId, int page, int size);

    KnowledgeDocumentContentVO documentContent(Long knowledgeBaseId, Long documentId);
}
```

- [ ] **Step 4: Implement the minimal administrator service**

Create `KnowledgeBaseAdminServiceImpl` with constructor injection of `KnowledgeBaseRepository` and `KnowledgeDocumentRepository`. Its core methods must be:

```java
@Service
public class KnowledgeBaseAdminServiceImpl implements KnowledgeBaseAdminService {
    private final KnowledgeBaseRepository baseRepository;
    private final KnowledgeDocumentRepository documentRepository;

    public KnowledgeBaseAdminServiceImpl(KnowledgeBaseRepository baseRepository,
                                         KnowledgeDocumentRepository documentRepository) {
        this.baseRepository = baseRepository;
        this.documentRepository = documentRepository;
    }

    @Override
    public PageResult<KnowledgeBaseAdminVO> page(int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = baseRepository.countAll();
        return new PageResult<>(baseRepository.findPageAll(normalizedPage, normalizedSize)
                        .stream().map(this::toBaseVO).toList(),
                total, normalizedPage, normalizedSize, pages(total, normalizedSize));
    }

    @Override
    public PageResult<KnowledgeDocumentVO> documentPage(Long knowledgeBaseId, int page, int size) {
        requireBase(knowledgeBaseId);
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        long total = documentRepository.countByKnowledgeBaseId(knowledgeBaseId);
        return new PageResult<>(documentRepository.findPageByKnowledgeBaseId(
                                knowledgeBaseId, normalizedPage, normalizedSize)
                        .stream().map(this::toDocumentVO).toList(),
                total, normalizedPage, normalizedSize, pages(total, normalizedSize));
    }

    @Override
    public KnowledgeDocumentContentVO documentContent(Long knowledgeBaseId, Long documentId) {
        requireBase(knowledgeBaseId);
        KnowledgeDocumentRecord document = documentRepository.findById(documentId)
                .filter(record -> !record.deleted())
                .filter(record -> knowledgeBaseId.equals(record.knowledgeBaseId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_NOT_FOUND));
        if (document.sourceType() != KnowledgeDocumentSourceType.TEXT) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED);
        }
        if (!StringUtils.hasText(document.rawContent())) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_DOCUMENT_PARSE_FAILED,
                    "Knowledge document content is empty");
        }
        return new KnowledgeDocumentContentVO(document.id(), document.title(), document.rawContent());
    }

    private KnowledgeBaseRecord requireBase(Long id) {
        return baseRepository.findNotDeletedById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.KNOWLEDGE_BASE_NOT_FOUND));
    }

    private KnowledgeBaseAdminVO toBaseVO(KnowledgeBaseRecord base) {
        return new KnowledgeBaseAdminVO(base.id(), base.userId(), base.name(), base.description(),
                base.embeddingModelId(), base.embeddingModelVersion(), base.pendingEmbeddingModelId(),
                base.pendingEmbeddingModelVersion(), base.activeIndexVersion(),
                base.pendingIndexVersion() != null, base.status(), base.createTime(), base.updateTime());
    }

    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocumentRecord document) {
        return new KnowledgeDocumentVO(document.id(), document.title(), document.sourceType(),
                document.fileId(), document.status(), document.indexVersion(),
                document.activeIndexVersion(), document.chunkCount(), document.errorCode(),
                document.errorMessage(), document.createTime(), document.updateTime());
    }

    private long pages(long total, int size) {
        return total == 0 ? 0 : (total + size - 1) / size;
    }
}
```

Use imports for `@Service`, `PageResult`, `BusinessException`, `ErrorCode`, the knowledge enums/models/repositories/services/VOs, and `StringUtils`. Do not add a current-user provider, file service, or write operation to this class.

- [ ] **Step 5: Run the service tests and verify they pass**

Run:

```bash
mvn -pl ai-platform-knowledge -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=KnowledgeBaseAdminServiceImplTest test
```

Expected: all administrator service tests pass.

---

### Task 3: Add the admin controller with MockMvc coverage

**Files:**
- Test: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminKnowledgeBaseControllerTest.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminKnowledgeBaseController.java`
- Modify: `ai-platform-admin/pom.xml`

- [ ] **Step 1: Add the direct knowledge-module dependency**

Add this dependency to `ai-platform-admin/pom.xml` alongside the existing module dependencies:

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-knowledge</artifactId>
    <version>${project.version}</version>
</dependency>
```

- [ ] **Step 2: Write the failing controller tests**

Create `AdminKnowledgeBaseControllerTest` with a mocked `KnowledgeBaseAdminService` and standalone MockMvc. Cover all three endpoints:

```java
@ExtendWith(MockitoExtension.class)
class AdminKnowledgeBaseControllerTest {
    @Mock
    private KnowledgeBaseAdminService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminKnowledgeBaseController(service)).build();
    }

    @Test
    void pagesKnowledgeBases() throws Exception {
        when(service.page(2, 30)).thenReturn(new PageResult<>(List.of(
                new KnowledgeBaseAdminVO(12L, 100L, "产品手册", "说明", 30L, 1,
                        null, null, 1L, false, KnowledgeBaseStatus.ACTIVE,
                        LocalDateTime.of(2026, 11, 5, 10, 0),
                        LocalDateTime.of(2026, 11, 5, 10, 0))),
                1, 2, 30, 1));

        mockMvc.perform(get("/api/admin/knowledge-bases")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(12))
                .andExpect(jsonPath("$.data.records[0].userId").value(100));

        verify(service).page(2, 30);
    }

    @Test
    void pagesDocumentsForKnowledgeBase() throws Exception {
        when(service.documentPage(12L, 1, 20)).thenReturn(new PageResult<>(List.of(
                new KnowledgeDocumentVO(21L, "制度", KnowledgeDocumentSourceType.TEXT, null,
                        KnowledgeDocumentStatus.READY, 1L, 1L, 3, null, null,
                        LocalDateTime.of(2026, 11, 5, 10, 0),
                        LocalDateTime.of(2026, 11, 5, 10, 0))),
                1, 1, 20, 1));

        mockMvc.perform(get("/api/admin/knowledge-bases/12/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(21))
                .andExpect(jsonPath("$.data.records[0].title").value("制度"));

        verify(service).documentPage(12L, 1, 20);
    }

    @Test
    void readsDocumentContent() throws Exception {
        when(service.documentContent(12L, 21L))
                .thenReturn(new KnowledgeDocumentContentVO(21L, "制度", "第一条"));

        mockMvc.perform(get("/api/admin/knowledge-bases/12/documents/21/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(21))
                .andExpect(jsonPath("$.data.content").value("第一条"));

        verify(service).documentContent(12L, 21L);
    }
}
```

Use the same package and imports as existing `AdminFileControllerTest`, plus knowledge service/VO/enums, `LocalDateTime`, `List`, Mockito, and MockMvc request/result helpers.

- [ ] **Step 3: Run the controller tests and verify they fail**

Run:

```bash
mvn -pl ai-platform-admin -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminKnowledgeBaseControllerTest test
```

Expected: compilation fails because `AdminKnowledgeBaseController` does not exist.

- [ ] **Step 4: Implement the controller**

Create `AdminKnowledgeBaseController`:

```java
package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.service.KnowledgeBaseAdminService;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-bases")
public class AdminKnowledgeBaseController {
    private final KnowledgeBaseAdminService service;

    public AdminKnowledgeBaseController(KnowledgeBaseAdminService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("knowledge:read")
    public ApiResponse<PageResult<KnowledgeBaseAdminVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.page(page, size));
    }

    @GetMapping("/{knowledgeBaseId}/documents")
    @SaCheckPermission("knowledge:read")
    public ApiResponse<PageResult<KnowledgeDocumentVO>> documents(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.documentPage(knowledgeBaseId, page, size));
    }

    @GetMapping("/{knowledgeBaseId}/documents/{documentId}/content")
    @SaCheckPermission("knowledge:read")
    public ApiResponse<KnowledgeDocumentContentVO> documentContent(
            @PathVariable("knowledgeBaseId") Long knowledgeBaseId,
            @PathVariable("documentId") Long documentId) {
        return ApiResponse.success(service.documentContent(knowledgeBaseId, documentId));
    }
}
```

The controller must not inject `CurrentUserProvider`, `KnowledgeBaseRepository`, or `KnowledgeDocumentRepository`; administrator scope is represented by the dedicated service.

- [ ] **Step 5: Run the controller tests and verify they pass**

Run:

```bash
mvn -pl ai-platform-admin -am -Dsurefire.failIfNoSpecifiedTests=false -Dtest=AdminKnowledgeBaseControllerTest test
```

Expected: all three MockMvc tests pass.

---

### Task 4: Register the read permission with a globally unique Flyway version

**Files:**
- Create: `ai-platform-admin/src/main/resources/db/migration/V12__admin_knowledge_permission.sql`

- [ ] **Step 1: Create the migration**

Use this exact SQL:

```sql
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Knowledge base read', 'knowledge:read', 3, 203, 1);
```

`V12` follows the current global MySQL migration sequence (`V11` is already used by the AI module), and sort `203` follows existing file permissions `200`–`202`.

- [ ] **Step 2: Verify migration uniqueness and contents**

Run:

```bash
find ai-platform-*/src/main/resources/db/migration -maxdepth 1 -type f -name 'V*.sql' -print | sort

grep -nE 'knowledge:read|203|insert ignore' \
  ai-platform-admin/src/main/resources/db/migration/V12__admin_knowledge_permission.sql
```

Expected: no other MySQL migration uses `V12`, and the output contains exactly the `knowledge:read` permission with sort `203`.

---

### Task 5: Document the administrator API contract

**Files:**
- Modify: `docs/frontend-api-integration.md`
- Modify: `docs/backend-development-guide.md`

- [ ] **Step 1: Add the frontend API section**

After the existing administrator file API section in `docs/frontend-api-integration.md`, add a “管理员知识库接口” section documenting this table:

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/admin/knowledge-bases?page=1&size=20` | `knowledge:read` | 分页查询全部未删除知识库 |
| `GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents?page=1&size=20` | `knowledge:read` | 查询指定知识库的未删除文档 |
| `GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/content` | `knowledge:read` | 查询文本文档原始内容 |

Document that the knowledge-base record contains the regular knowledge-base fields plus `userId`; document records use the existing `KnowledgeDocumentVO` fields. State that page is normalized to at least 1 and size is limited to 1–100, deleted records are excluded, file documents are not returned by the content endpoint, and file documents can be accessed through the existing administrator file endpoints using `fileId`.

Include this response example for the knowledge-base record:

```json
{
  "id": 12,
  "userId": 100,
  "name": "产品手册",
  "description": "产品相关资料",
  "embeddingModelId": 30,
  "embeddingModelVersion": 1,
  "pendingEmbeddingModelId": null,
  "pendingEmbeddingModelVersion": null,
  "activeIndexVersion": 1,
  "reindexing": false,
  "status": "ACTIVE",
  "createTime": "2026-11-05T10:00:00",
  "updateTime": "2026-11-05T10:00:00"
}
```

- [ ] **Step 2: Update the backend module index**

In `docs/backend-development-guide.md`:

- change the admin module description to include “知识库查询”；
- add `AdminKnowledgeBaseController` and `KnowledgeBaseAdminService` to the module/location index;
- state that knowledge-base administrator queries remain on the dedicated PostgreSQL data source and do not perform a cross-database user join.

- [ ] **Step 3: Check the documentation for stale API statements**

Run:

```bash
grep -nE '管理员知识库|admin/knowledge-bases|knowledge:read|KnowledgeBaseAdminService|AdminKnowledgeBaseController' \
  docs/frontend-api-integration.md docs/backend-development-guide.md
```

Expected: both documentation files mention the new paths, permission, and module entry points.

---

### Task 6: Run the complete verification suite

**Files:**
- No additional files; review all changes from Tasks 1–5.

- [ ] **Step 1: Run focused knowledge-module tests**

Run:

```bash
mvn -pl ai-platform-knowledge -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=KnowledgeBaseAdminServiceImplTest,KnowledgeMapperXmlTest test
```

Expected: administrator service tests pass; mapper tests pass when Docker is available or are cleanly skipped by Testcontainers when Docker is unavailable.

- [ ] **Step 2: Run focused admin-module tests**

Run:

```bash
mvn -pl ai-platform-admin -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=AdminKnowledgeBaseControllerTest,AdminFileControllerTest test
```

Expected: both administrator controller test classes pass.

- [ ] **Step 3: Run the full Maven test suite**

Run:

```bash
mvn test
```

Expected: all modules compile and tests pass; any Testcontainers-only tests may be skipped only through their existing `disabledWithoutDocker = true` configuration.

- [ ] **Step 4: Review the final diff for scope and security**

Run:

```bash
git diff --check
git diff --stat
git status --short
```

Confirm that the diff contains only the administrator read feature, docs, and its tests; `.DS_Store` remains untracked and is not added. Confirm there are no new write endpoints, no current-user bypass added to ordinary user services, no cross-database join, and every administrator controller method has `@SaCheckPermission("knowledge:read")`.

Do not create a git commit automatically; leave the implementation changes available for review unless the user explicitly requests a commit.
