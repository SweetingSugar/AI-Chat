# 管理文件查询日期格式调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让管理员文件查询接口使用 `yyyy-MM-dd` 日期参数，并按完整自然日过滤文件创建时间。

**Architecture:** 将文件查询 DTO 的日期字段建模为 `LocalDate`，由 Spring MVC 按 `yyyy-MM-dd` 绑定；文件服务在构造数据库条件时把起始日转换为当天零点，把结束日转换为次日零点并使用小于条件。Controller 路由保持不变，日期语义只在 DTO 和服务查询边界处处理。

**Tech Stack:** Java 17、Spring Boot 3.5 Spring MVC、MyBatis-Plus、JUnit 5、Mockito、AssertJ、Maven。

---

## 文件变更清单

- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/dto/FileQueryRequest.java` — 将日期字段改为 `LocalDate` 并绑定 `yyyy-MM-dd`。
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java:121-133` — 将日期转换为完整自然日的数据库时间边界。
- Modify: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminFileControllerTest.java` — 增加日期参数绑定测试。
- Modify: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java` — 增加自然日边界查询测试。
- Modify: `docs/frontend-api-integration.md:347-353` — 补充管理员文件接口日期参数示例和格式。

## Task 1: 先为日期参数绑定增加失败测试

**Files:**
- Modify: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminFileControllerTest.java:35-45`

- [ ] **Step 1: 为 admin Controller 测试增加日期参数请求**

在现有 `pagesFiles()` 测试后增加以下测试。它发送 `yyyy-MM-dd` 参数，捕获 Controller 传给服务的 `FileQueryRequest`，并断言日期值已正确绑定：

```java
@Test
void acceptsDateOnlyQueryParameters() throws Exception {
    when(service.page(any())).thenReturn(new PageResult<>(List.of(), 0, 1, 20, 0));

    mockMvc.perform(get("/api/admin/files")
                    .param("createdFrom", "2026-10-01")
                    .param("createdTo", "2026-10-27"))
            .andExpect(status().isOk());

    ArgumentCaptor<FileQueryRequest> requestCaptor = ArgumentCaptor.forClass(FileQueryRequest.class);
    verify(service).page(requestCaptor.capture());
    assertThat(requestCaptor.getValue().getCreatedFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(requestCaptor.getValue().getCreatedTo()).isEqualTo(LocalDate.of(2026, 10, 27));
}
```

同时增加以下 import：

```java
import com.example.aichat.file.dto.FileQueryRequest;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
```

- [ ] **Step 2: 运行 Controller 测试并确认日期格式测试当前失败**

Run:

```bash
"$HOME/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn" -pl ai-platform-admin -am -Dtest=com.example.aichat.admin.controller.AdminFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 现有列表测试通过，新增日期参数测试因 `FileQueryRequest` 仍使用 `LocalDateTime` 和 `ISO.DATE_TIME`，无法将 `2026-10-01` 绑定为日期时间而失败。

## Task 2: 修改查询 DTO 的日期类型和格式

**Files:**
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/dto/FileQueryRequest.java:6,15-18,68-83`

- [ ] **Step 1: 将日期字段改为 LocalDate 并指定年月日格式**

将 import 和两个字段、getter/setter 修改为以下形式：

```java
import java.time.LocalDate;
```

```java
@DateTimeFormat(pattern = "yyyy-MM-dd")
private LocalDate createdFrom;
@DateTimeFormat(pattern = "yyyy-MM-dd")
private LocalDate createdTo;
```

```java
public LocalDate getCreatedFrom() {
    return createdFrom;
}

public void setCreatedFrom(LocalDate createdFrom) {
    this.createdFrom = createdFrom;
}

public LocalDate getCreatedTo() {
    return createdTo;
}

public void setCreatedTo(LocalDate createdTo) {
    this.createdTo = createdTo;
}
```

不要修改分页、用户、文件名、内容类型和状态字段。

- [ ] **Step 2: 重新运行 Controller 测试验证日期绑定通过**

Run:

```bash
"$HOME/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn" -pl ai-platform-admin -am -Dtest=com.example.aichat.admin.controller.AdminFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `AdminFileControllerTest` 的 2 个测试全部通过，`2026-10-01` 和 `2026-10-27` 分别绑定为对应的 `LocalDate`。

## Task 3: 为服务层自然日边界增加失败测试并实现转换

**Files:**
- Modify: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java:18-26,119-137`
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java:121-133`

- [ ] **Step 1: 增加服务层日期边界测试**

在 `FileServiceImplTest` 增加以下 import：

```java
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.file.dto.FileQueryRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;
```

在现有测试方法前增加以下测试：

```java
@Test
void queriesCompleteCalendarDateRange() {
    when(mapper.selectPage(any(), any())).thenReturn(new Page<>());

    FileQueryRequest request = new FileQueryRequest();
    request.setCreatedFrom(LocalDate.of(2026, 10, 1));
    request.setCreatedTo(LocalDate.of(2026, 10, 27));

    service.page(request);

    ArgumentCaptor<QueryWrapper<FileMetadata>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
    verify(mapper).selectPage(any(), queryCaptor.capture());
    assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
            .containsExactlyInAnyOrder(
                    LocalDateTime.of(2026, 10, 1, 0, 0),
                    LocalDateTime.of(2026, 10, 28, 0, 0));
}
```

- [ ] **Step 2: 运行 file 服务测试并确认新增边界测试失败**

Run:

```bash
"$HOME/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn" -pl ai-platform-file -Dtest=com.example.aichat.file.service.FileServiceImplTest test
```

Expected: 测试编译成功但新增边界测试失败，因为 DTO 已提供 `LocalDate`，而当前服务仍把 `LocalDate` 直接作为 MyBatis-Plus 查询参数，尚未转换为 `LocalDateTime`。

- [ ] **Step 3: 在 FileServiceImpl 中转换查询边界**

将 `page` 方法中的日期条件改为：

```java
if (request.getCreatedFrom() != null) {
    query.ge("create_time", request.getCreatedFrom().atStartOfDay());
}
if (request.getCreatedTo() != null) {
    query.lt("create_time", request.getCreatedTo().plusDays(1).atStartOfDay());
}
```

保持其他查询条件、排序、分页和返回值不变。`createdTo` 使用次日零点的 `<` 条件，避免丢失结束日包含的时间部分。

- [ ] **Step 4: 运行 file 服务测试验证自然日边界**

Run:

```bash
"$HOME/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn" -pl ai-platform-file -Dtest=com.example.aichat.file.service.FileServiceImplTest test
```

Expected: `FileServiceImplTest` 全部通过，新增测试捕获到 `2026-10-01T00:00` 和 `2026-10-28T00:00` 两个查询参数。

## Task 4: 更新接口文档并完成回归验证

**Files:**
- Modify: `docs/frontend-api-integration.md:347-353`

- [ ] **Step 1: 补充管理员文件接口日期参数文档**

将管理员文件列表接口示例更新为：

```markdown
| `GET /api/admin/files?page=1&size=20&createdFrom=2026-10-01&createdTo=2026-10-27` | `file:read` | 分页查询文件，日期参数格式为 `yyyy-MM-dd`，范围包含起止日期 |
```

详情和删除接口保持原有内容不变。

- [ ] **Step 2: 运行 file 和 admin 相关测试**

Run:

```bash
"$HOME/.m2/wrapper/dists/apache-maven-3.9.16/56ba1f9f/bin/mvn" -pl ai-platform-admin -am test
```

Expected: file、system、ai、chat 和 admin 模块可以编译；本次新增的 Controller 日期绑定测试和服务层日期边界测试通过。若其他已有 Ollama 测试仍失败，应记录其失败信息，不修改无关代码。

- [ ] **Step 3: 检查日期格式和工作区差异**

Run:

```bash
rg -n "createdFrom|createdTo|yyyy-MM-dd|ISO.DATE_TIME" ai-platform-file ai-platform-admin docs/frontend-api-integration.md --glob '!**/target/**'
git diff --check
git status --short
```

Expected: `FileQueryRequest` 的两个日期字段均为 `LocalDate` 并使用 `yyyy-MM-dd`，文件服务只在查询边界处生成 `LocalDateTime`；没有 `ISO.DATE_TIME` 残留在这两个字段上，且没有空白错误。
