# AdminFileController 模块归属调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将文件管理端 Controller 归入 `ai-platform-admin`，同时保持现有管理文件接口的路由、权限和响应行为不变。

**Architecture:** `ai-platform-admin` 直接依赖 `ai-platform-file`，管理端 Controller 放在 admin 模块；文件领域服务、DTO/VO、普通文件接口和持久化能力继续放在 file 模块。启动模块同时加载两个模块，因此迁移只改变代码归属，不改变运行时 URL。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring MVC、Sa-Token、JUnit 5、Mockito、Maven 多模块构建。

---

## 文件变更清单

- Modify: `ai-platform-admin/pom.xml` — 增加 `ai-platform-file` 依赖。
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminFileController.java` — 管理端文件 Controller。
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminFileControllerTest.java` — 迁移后的 Controller 测试。
- Delete: `ai-platform-file/src/main/java/com/example/aichat/file/controller/AdminFileController.java` — 删除旧归属的 Controller。
- Delete: `ai-platform-file/src/test/java/com/example/aichat/file/controller/AdminFileControllerTest.java` — 删除旧归属的测试。

## Task 1: 为 admin 模块声明文件模块依赖

**Files:**
- Modify: `ai-platform-admin/pom.xml:11-47`

- [ ] **Step 1: 在 admin 依赖列表中加入 file 模块依赖**

在 `ai-platform-admin/pom.xml` 的 `<dependencies>` 中加入以下依赖，建议放在 `ai-platform-system` 依赖之前：

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-file</artifactId>
    <version>${project.version}</version>
</dependency>
```

该依赖使 admin Controller 能直接使用 `FileService`、`FileQueryRequest` 和 `FileMetadataVO`。不修改 `ai-platform-file/pom.xml`，避免 file 反向依赖 admin。

## Task 2: 迁移并先验证 Controller 测试

**Files:**
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminFileControllerTest.java`
- Delete: `ai-platform-file/src/test/java/com/example/aichat/file/controller/AdminFileControllerTest.java`

- [ ] **Step 1: 创建 admin 模块测试目录和测试文件**

将测试迁移到 admin 包，并使用以下完整内容：

```java
package com.example.aichat.admin.controller;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminFileControllerTest {

    @Mock
    private FileService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminFileController(service)).build();
    }

    @Test
    void pagesFiles() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(
                new FileMetadataVO(1L, 100L, "a.txt", "text/plain", 5L, null, null, null)),
                1, 1, 20, 1));

        mockMvc.perform(get("/api/admin/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }
}
```

- [ ] **Step 2: 运行测试并确认当前因 Controller 尚未迁移而无法编译**

Run:

```bash
mvn -pl ai-platform-admin -am -Dtest=com.example.aichat.admin.controller.AdminFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: 失败，原因是 `com.example.aichat.admin.controller.AdminFileController` 尚不存在。该失败确认测试已经指向 admin 包，而不是继续依赖 file 模块中的旧类。

## Task 3: 将 Controller 实现迁移到 admin 模块

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminFileController.java`
- Delete: `ai-platform-file/src/main/java/com/example/aichat/file/controller/AdminFileController.java`

- [ ] **Step 1: 在 admin 模块创建 Controller**

创建文件并使用以下完整内容：

```java
package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/files")
public class AdminFileController {
    private final FileService service;

    public AdminFileController(FileService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("file:read")
    public ApiResponse<PageResult<FileMetadataVO>> page(@ModelAttribute FileQueryRequest request) {
        return ApiResponse.success(service.page(request));
    }

    @GetMapping("/{fileId}")
    @SaCheckPermission("file:read")
    public ApiResponse<FileMetadataVO> get(@PathVariable("fileId") Long fileId) {
        return ApiResponse.success(service.findByAdmin(fileId));
    }

    @DeleteMapping("/{fileId}")
    @SaCheckPermission("file:delete")
    public ApiResponse<Void> delete(@PathVariable("fileId") Long fileId) {
        service.deleteByAdmin(fileId);
        return ApiResponse.success(null);
    }
}
```

- [ ] **Step 2: 删除 file 模块中的旧 Controller**

删除 `ai-platform-file/src/main/java/com/example/aichat/file/controller/AdminFileController.java`，确保 Spring 扫描路径中只有一份 `/api/admin/files` Controller。

- [ ] **Step 3: 删除 file 模块中的旧测试**

删除 `ai-platform-file/src/test/java/com/example/aichat/file/controller/AdminFileControllerTest.java`，避免测试重复并确保测试归属与生产代码一致。

## Task 4: 运行迁移后的验证

**Files:**
- Verify: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminFileController.java`
- Verify: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminFileControllerTest.java`
- Verify: `ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java`

- [ ] **Step 1: 运行迁移后的 Controller 测试**

Run:

```bash
mvn -pl ai-platform-admin -am -Dtest=com.example.aichat.admin.controller.AdminFileControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `AdminFileControllerTest` 通过，列表请求返回 HTTP 200、`success=true`，且首条记录 ID 为 `1`。

- [ ] **Step 2: 编译并测试 admin 及其依赖模块**

Run:

```bash
mvn -pl ai-platform-admin -am test
```

Expected: Maven 构建成功，file、system、ai、chat 和 admin 模块的测试均通过。

- [ ] **Step 3: 检查 Controller 和接口引用只保留一份**

Run:

```bash
rg -n "class AdminFileController|/api/admin/files|file:read|file:delete" ai-platform-file ai-platform-admin
```

Expected: `AdminFileController` 类只出现在 `ai-platform-admin`；管理接口路由和两个权限标识只由迁移后的 Controller 使用，`FileController` 仍保留普通 `/api/files` 接口。

- [ ] **Step 4: 检查工作区变更范围**

Run:

```bash
git status --short
```

Expected: 只包含本次设计文档、计划文档以及上述 Controller、测试和 `ai-platform-admin/pom.xml` 的迁移变更；不修改 `.DS_Store` 或其他无关文件。
