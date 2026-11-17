# Available Tools Query Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /api/tools/available` so any authenticated user can list enabled, non-stale tools without requiring an administrator or tool permission.

**Architecture:** Add a read-only `ToolQueryService` with a safe `AvailableToolView` response model. The service filters enabled and `AVAILABLE` definitions, checks that MCP tools belong to an enabled server, parses the stored input schema, and is exposed by the existing `ToolExecutionController` without `@SaCheckPermission`; the existing execute endpoint remains permission-protected.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC, MyBatis-Plus, Jackson, Sa-Token, JUnit 5, Mockito, MockMvc.

---

## File map

- **Create:** `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/query/AvailableToolView.java`
  - Public-safe response record containing only tool code, display metadata, type, and input schema.
- **Create:** `ai-platform-tool/src/main/java/com/example/aichat/tool/service/ToolQueryService.java`
  - Read-only service contract exposing `listAvailable()`.
- **Create:** `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolQueryServiceImpl.java`
  - Applies status/availability/MCP-server filtering and maps entities to `AvailableToolView`.
- **Modify:** `ai-platform-tool/src/main/java/com/example/aichat/tool/controller/ToolExecutionController.java`
  - Inject the query service and add the unpermissioned `GET /available` method while preserving `POST /execute`.
- **Create:** `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolQueryServiceImplTest.java`
  - Unit tests for filtering, ordering, MCP server handling, schema parsing, and response-field safety.
- **Create:** `ai-platform-tool/src/test/java/com/example/aichat/tool/controller/ToolExecutionControllerTest.java`
  - MockMvc contract test for the new route and reflection check that only execution has `@SaCheckPermission`.
- **Modify:** `docs/backend-development-guide.md`
  - Document the authenticated, non-admin tool listing endpoint and filtering rules.
- **Modify:** `docs/frontend-api-integration.md`
  - Document request headers, response fields, and examples for frontend consumers.

No database migration, mapper interface, execution service, or authentication configuration changes are needed. MyBatis-Plus `@TableLogic` already excludes deleted `ToolDefinition` and `ToolMcpServer` rows from mapper queries.

---

### Task 1: Add the safe query model and service behavior

**Files:**
- Create: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolQueryServiceImplTest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/query/AvailableToolView.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/ToolQueryService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolQueryServiceImpl.java`

- [ ] **Step 1: Write the failing service tests.**

Create `ToolQueryServiceImplTest.java` with Mockito mocks for `ToolDefinitionMapper` and `McpServerManagementService`. The test fixture must include eligible HTTP/MCP tools plus disabled, stale, logically deleted, missing-server, missing-ID, and disabled-server cases. Use the following test shape so the service contract is explicit:

```java
package com.example.aichat.tool.service.impl;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolQueryServiceImplTest {
    @Mock
    private ToolDefinitionMapper mapper;
    @Mock
    private McpServerManagementService serverService;

    private ToolQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ToolQueryServiceImpl(mapper, serverService, new ObjectMapper());
    }

    @Test
    void listsOnlyEnabledAvailableToolsWithEnabledMcpServersInIdOrder() {
        ToolDefinition http = tool(2L, "http_tool", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null,
                "{\"type\":\"object\"}");
        ToolDefinition mcp = tool(4L, "mcp_tool", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 8L,
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}");
        ToolDefinition disabled = tool(5L, "disabled", ToolType.HTTP, 0, ToolAvailability.AVAILABLE, null, null);
        ToolDefinition stale = tool(6L, "stale", ToolType.HTTP, 1, ToolAvailability.STALE, null, null);
        ToolDefinition deleted = tool(7L, "deleted", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null, null);
        deleted.setDeleted(1);
        ToolDefinition noServerId = tool(8L, "no_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, null, null);
        ToolDefinition missingServer = tool(9L, "missing_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 9L, null);
        ToolDefinition disabledServer = tool(10L, "disabled_server", ToolType.MCP, 1, ToolAvailability.AVAILABLE, 10L, null);

        when(mapper.selectList(any())).thenReturn(List.of(
                mcp, disabledServer, stale, http, deleted, noServerId, missingServer, disabled));
        when(serverService.getRequired(8L)).thenReturn(server(8L, 1));
        when(serverService.getRequired(9L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MCP server not found"));
        when(serverService.getRequired(10L)).thenReturn(server(10L, 0));

        List<AvailableToolView> result = service.listAvailable();

        assertThat(result).extracting(AvailableToolView::toolCode)
                .containsExactly("http_tool", "mcp_tool");
        assertThat(result.get(0).inputSchema().path("type").asText()).isEqualTo("object");
        assertThat(result.get(1).inputSchema().path("properties").path("city").path("type").asText())
                .isEqualTo("string");
    }

    @Test
    void rejectsInvalidStoredInputSchemaWithToolConfigError() {
        when(mapper.selectList(any())).thenReturn(List.of(
                tool(1L, "broken", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null, "not-json")));

        assertThatThrownBy(() -> service.listAvailable())
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.TOOL_CONFIG_INVALID);
    }

    @Test
    void responseModelContainsNoManagementOrCredentialFields() throws Exception {
        when(mapper.selectList(any())).thenReturn(List.of(
                tool(1L, "safe", ToolType.HTTP, 1, ToolAvailability.AVAILABLE, null,
                        "{\"type\":\"object\"}")));

        String json = new ObjectMapper().writeValueAsString(service.listAvailable().get(0));

        assertThat(json).contains("toolCode", "toolName", "description", "toolType", "inputSchema")
                .doesNotContain("httpConfig", "authType", "authHeaderName", "hasCredential",
                        "credentialVersion", "mcpServerId", "status", "availability", "credential");
    }

    private ToolDefinition tool(Long id, String code, ToolType type, int status,
                                ToolAvailability availability, Long serverId, String schema) {
        ToolDefinition tool = new ToolDefinition();
        tool.setId(id);
        tool.setToolCode(code);
        tool.setToolName(code + " name");
        tool.setDescription(code + " description");
        tool.setToolType(type);
        tool.setStatus(status);
        tool.setAvailability(availability);
        tool.setMcpServerId(serverId);
        tool.setInputSchema(schema);
        tool.setDeleted(0);
        return tool;
    }

    private ToolMcpServer server(Long id, int status) {
        ToolMcpServer server = new ToolMcpServer();
        server.setId(id);
        server.setStatus(status);
        return server;
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails.**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolQueryServiceImplTest test
```

Expected: compilation fails because `AvailableToolView` and `ToolQueryServiceImpl` do not exist yet.

- [ ] **Step 3: Add the public-safe response record and service contract.**

Create `AvailableToolView.java`:

```java
package com.example.aichat.tool.dto.query;

import com.example.aichat.tool.enums.ToolType;
import com.fasterxml.jackson.databind.JsonNode;

public record AvailableToolView(
        String toolCode,
        String toolName,
        String description,
        ToolType toolType,
        JsonNode inputSchema) {
}
```

Create `ToolQueryService.java`:

```java
package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.query.AvailableToolView;

import java.util.List;

public interface ToolQueryService {
    List<AvailableToolView> listAvailable();
}
```

- [ ] **Step 4: Implement the minimum query and mapping logic.**

Create `ToolQueryServiceImpl.java` with a Spring `@Service`. Query status and availability in the MyBatis wrapper, retain defensive Java filtering for mock/corrupt rows, rely on `@TableLogic` for normal deleted-row exclusion, and treat a missing MCP server as unavailable rather than failing the entire list:

```java
package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ToolQueryServiceImpl implements ToolQueryService {
    private final ToolDefinitionMapper mapper;
    private final McpServerManagementService serverService;
    private final ObjectMapper objectMapper;

    public ToolQueryServiceImpl(ToolDefinitionMapper mapper,
                                McpServerManagementService serverService,
                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.serverService = serverService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AvailableToolView> listAvailable() {
        return mapper.selectList(new LambdaQueryWrapper<ToolDefinition>()
                        .eq(ToolDefinition::getStatus, 1)
                        .eq(ToolDefinition::getAvailability, ToolAvailability.AVAILABLE)
                        .orderByAsc(ToolDefinition::getId))
                .stream()
                .filter(this::isEnabledAndAvailable)
                .filter(this::hasEnabledMcpServer)
                .map(this::toView)
                .toList();
    }

    private boolean isEnabledAndAvailable(ToolDefinition tool) {
        return !Integer.valueOf(1).equals(tool.getDeleted())
                && Integer.valueOf(1).equals(tool.getStatus())
                && tool.getAvailability() == ToolAvailability.AVAILABLE;
    }

    private boolean hasEnabledMcpServer(ToolDefinition tool) {
        if (tool.getToolType() != ToolType.MCP) {
            return true;
        }
        if (tool.getMcpServerId() == null) {
            return false;
        }
        try {
            ToolMcpServer server = serverService.getRequired(tool.getMcpServerId());
            return Integer.valueOf(1).equals(server.getStatus());
        } catch (BusinessException exception) {
            return false;
        }
    }

    private AvailableToolView toView(ToolDefinition tool) {
        return new AvailableToolView(tool.getToolCode(), tool.getToolName(), tool.getDescription(),
                tool.getToolType(), parseSchema(tool.getInputSchema()));
    }

    private JsonNode parseSchema(String schema) {
        if (!StringUtils.hasText(schema)) {
            return null;
        }
        try {
            return objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                    "Tool input schema is invalid", exception);
        }
    }
}
```

- [ ] **Step 5: Run the focused service tests to verify they pass.**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolQueryServiceImplTest test
```

Expected: all `ToolQueryServiceImplTest` tests pass.

- [ ] **Step 6: Commit the service slice.**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/dto/query/AvailableToolView.java \
  ai-platform-tool/src/main/java/com/example/aichat/tool/service/ToolQueryService.java \
  ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolQueryServiceImpl.java \
  ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolQueryServiceImplTest.java
git commit -m "feat: add available tool query service"
```

---

### Task 2: Expose the authenticated endpoint without a permission check

**Files:**
- Create: `ai-platform-tool/src/test/java/com/example/aichat/tool/controller/ToolExecutionControllerTest.java`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/controller/ToolExecutionController.java`

- [ ] **Step 1: Write the failing controller contract test.**

Create `ToolExecutionControllerTest.java` using the repository's standalone MockMvc pattern. The test verifies the route, response fields, delegation, and annotation distinction:

```java
package com.example.aichat.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.service.ToolQueryService;
import com.example.aichat.tool.enums.ToolType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ToolExecutionControllerTest {
    @Mock
    private ToolExecutionService executionService;
    @Mock
    private ToolQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ToolExecutionController(executionService, queryService)).build();
    }

    @Test
    void listsAvailableToolsWithoutPermissionAnnotation() throws Exception {
        when(queryService.listAvailable()).thenReturn(List.of(
                new AvailableToolView("weather_current", "Current weather", "Query weather",
                        ToolType.MCP, new ObjectMapper().readTree("{\"type\":\"object\"}"))));

        mockMvc.perform(get("/api/tools/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].toolCode").value("weather_current"))
                .andExpect(jsonPath("$.data[0].toolName").value("Current weather"))
                .andExpect(jsonPath("$.data[0].toolType").value("MCP"))
                .andExpect(jsonPath("$.data[0].inputSchema.type").value("object"))
                .andExpect(jsonPath("$.data[0].httpConfig").doesNotExist())
                .andExpect(jsonPath("$.data[0].credentialVersion").doesNotExist());

        verify(queryService).listAvailable();
    }

    @Test
    void keepsPermissionCheckOnlyOnExecutionMethod() throws Exception {
        Method listMethod = ToolExecutionController.class.getDeclaredMethod("listAvailable");
        Method executeMethod = ToolExecutionController.class.getDeclaredMethod(
                "execute", com.example.aichat.tool.dto.execution.ToolExecuteRequest.class);

        assertThat(listMethod.getAnnotation(SaCheckPermission.class)).isNull();
        assertThat(executeMethod.getAnnotation(SaCheckPermission.class)).isNotNull();
        assertThat(executeMethod.getAnnotation(SaCheckPermission.class).value())
                .isEqualTo("tool:execute");
    }
}
```

- [ ] **Step 2: Run the controller test to verify it fails.**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionControllerTest test
```

Expected: compilation fails because `ToolExecutionController` has no constructor accepting `ToolQueryService` and no `listAvailable()` method.

- [ ] **Step 3: Extend the existing controller with the new route.**

Modify `ToolExecutionController.java` to inject both services and add the method below. Keep the existing `execute` method and its annotation unchanged:

```java
package com.example.aichat.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.service.ToolQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolExecutionController {
    private final ToolExecutionService service;
    private final ToolQueryService queryService;

    public ToolExecutionController(ToolExecutionService service, ToolQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/available")
    public ApiResponse<List<AvailableToolView>> listAvailable() {
        return ApiResponse.success(queryService.listAvailable());
    }

    @PostMapping("/execute")
    @SaCheckPermission("tool:execute")
    public ApiResponse<ToolExecutionResult> execute(@Valid @RequestBody ToolExecuteRequest request) {
        return ApiResponse.success(service.execute(request));
    }
}
```

- [ ] **Step 4: Run the controller test to verify it passes.**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionControllerTest test
```

Expected: both controller tests pass, including the reflection assertion that `listAvailable()` has no `@SaCheckPermission` and `execute()` still requires `tool:execute`.

- [ ] **Step 5: Commit the endpoint slice.**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/controller/ToolExecutionController.java \
  ai-platform-tool/src/test/java/com/example/aichat/tool/controller/ToolExecutionControllerTest.java
git commit -m "feat: expose available tools endpoint"
```

---

### Task 3: Document the authenticated tool listing contract

**Files:**
- Modify: `docs/backend-development-guide.md`
- Modify: `docs/frontend-api-integration.md`

- [ ] **Step 1: Add the backend API description.**

Insert a `### 3.8 可用工具查询` subsection after the existing file/attachment subsection and before `## 4 数据存储` in `docs/backend-development-guide.md`:

```markdown
### 3.8 可用工具查询

工具列表接口面向所有已登录用户开放，不要求管理员身份，也不要求 `tool:read` 或 `tool:execute` 权限。

| 功能 | 接口 | 认证 / 权限 |
| --- | --- | --- |
| 查询可用工具 | `GET /api/tools/available` | 需要登录；不需要功能权限 |

接口只返回未逻辑删除、已启用且 `availability = AVAILABLE` 的工具。MCP 工具还必须关联到已启用的 MCP 服务；停用、过期、关联服务不存在或已停用的工具会被过滤。返回字段只有 `toolCode`、`toolName`、`description`、`toolType` 和 `inputSchema`，不包含 URL、认证配置、凭证或管理状态。
```

- [ ] **Step 2: Add the frontend request and response contract.**

Insert a `### 3.6 可用工具` subsection after the existing regenerate subsection and before `## 4. 文件接口` in `docs/frontend-api-integration.md`:

```markdown
### 3.6 可用工具

`GET /api/tools/available`

需要登录，但不需要管理员身份、`tool:read` 或 `tool:execute` 权限。请求必须携带登录响应中的动态 Token Header：

```http
GET /api/tools/available
{{tokenName}}: {{tokenValue}}
```

成功时 `data` 为工具数组；没有符合条件的工具时返回空数组：

```json
[
  {
    "toolCode": "weather_current",
    "toolName": "当前天气",
    "description": "查询当前天气",
    "toolType": "MCP",
    "inputSchema": {
      "type": "object",
      "properties": {
        "city": { "type": "string" }
      },
      "required": ["city"]
    }
  }
]
```

后端只返回已启用且 `AVAILABLE` 的工具；MCP 工具的关联服务也必须启用。响应不会包含 HTTP 地址、认证头、凭证、MCP 服务 ID 或其他管理字段。未登录访问返回 `401 / UNAUTHENTICATED`。
```

- [ ] **Step 3: Review the documentation diff for contract consistency.**

Run:

```bash
git diff --check -- docs/backend-development-guide.md docs/frontend-api-integration.md
git diff -- docs/backend-development-guide.md docs/frontend-api-integration.md
```

Expected: no whitespace errors; both documents use `/api/tools/available`, require login, omit permission requirements, and describe exactly the five response fields.

- [ ] **Step 4: Commit the documentation slice.**

```bash
git add docs/backend-development-guide.md docs/frontend-api-integration.md
git commit -m "docs: document available tools endpoint"
```

---

### Task 4: Run regression verification and inspect the final diff

**Files:**
- No additional files; verify the files changed in Tasks 1–3.

- [ ] **Step 1: Run all tool-module tests.**

```bash
mvn -pl ai-platform-tool -am test
```

Expected: Maven exits with code 0 and all tool-module tests pass, including management, discovery, execution, query, and controller tests.

- [ ] **Step 2: Run the full multi-module test suite.**

```bash
mvn test
```

Expected: Maven exits with code 0 with no regressions in AI, chat, admin, system, file, knowledge, or tool modules.

- [ ] **Step 3: Inspect the final diff and staged state.**

```bash
git diff HEAD~3..HEAD --stat
git diff HEAD~3..HEAD -- ai-platform-tool docs/backend-development-guide.md docs/frontend-api-integration.md
git status --short
```

Confirm that the implementation:

- adds only `GET /api/tools/available` for the new behavior;
- has no `@SaCheckPermission` on `listAvailable()`;
- retains `@SaCheckPermission("tool:execute")` on `execute()`;
- does not add endpoint parameters allowing callers to choose an endpoint or expose credentials;
- filters disabled/stale/deleted tools and disabled/missing MCP servers;
- returns only the five documented fields;
- leaves the pre-existing untracked `.DS_Store` untouched.

- [ ] **Step 4: If verification changed no files, report the test commands and results without creating an empty commit.**

If a test or formatter changes a tracked file, review that diff before deciding whether it belongs in a separate fix commit; do not stage `.DS_Store`.

---

## Self-review checklist

- **Spec coverage:** Tasks 1 and 2 implement the safe response, availability rules, MCP server filtering, authenticated route, and preserved execution permission. Task 3 documents both backend and frontend contracts. Task 4 covers module and full-suite regression tests.
- **Placeholder scan:** No step depends on `TODO`, `TBD`, or an unspecified future decision; every new path, method, field, test command, and expected result is named.
- **Type consistency:** `AvailableToolView`, `ToolQueryService.listAvailable()`, `ToolQueryServiceImpl`, and `ToolExecutionController.listAvailable()` use the same `List<AvailableToolView>` signature. The controller constructor and test setup both accept `(ToolExecutionService, ToolQueryService)`.
- **Security consistency:** The new route relies on the existing `/api/**` login filter and deliberately has no permission annotation. Sensitive management fields are absent from the response record and are checked by JSON serialization in the service/controller tests.
