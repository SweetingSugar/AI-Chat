# 管理员查看所有 Agent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为具有 `agent:read` 权限的管理员新增所有用户 Agent 的分页列表和详情查询，同时保持普通用户 Agent 接口的用户隔离不变。

**Architecture:** Agent 模块继续拥有 Agent 实体、绑定表和查询逻辑，在 `AgentService` 中增加管理员范围的 `adminPage` 与 `adminGet` 方法。Admin 模块新增薄的 `AdminAgentController`，只负责 `/api/admin/agents` 路由与 `agent:read` 权限校验，不直接访问 Agent Mapper；响应使用独立的 `AgentAdminVO`，带有 `userId` 以标识所属用户。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring MVC, MyBatis-Plus, Flyway MySQL migrations, Sa-Token, JUnit 5, Mockito, MockMvc, AssertJ.

---

## 文件地图

### 新增文件

- `ai-platform-agent/src/main/java/com/example/aichat/agent/vo/AgentAdminVO.java`
  - 管理员查看 Agent 的响应记录，包含所属用户 ID 和绑定资源。
- `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminAgentController.java`
  - 管理员 Agent 列表和详情 REST 接口。
- `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminAgentControllerTest.java`
  - 管理员接口路径、响应和权限注解测试。
- `ai-platform-admin/src/main/resources/db/migration/V16__admin_agent_permission.sql`
  - `agent:read` 权限种子。
- `ai-platform-agent/src/main/resources/db/migration/V17__admin_agent_page_index.sql`
  - 为管理员跨用户排序分页增加复合索引。

### 修改文件

- `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentService.java`
  - 增加 `adminPage` 和 `adminGet` 方法声明。
- `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentServiceImpl.java`
  - 实现管理员范围的未删除 Agent 查询和绑定映射。
- `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`
  - 增加管理员列表、详情、未找到和不触发资源校验测试。
- `ai-platform-admin/pom.xml`
  - 显式声明 `ai-platform-agent` 依赖。
- `docs/backend-development-guide.md`
  - 补充管理员 Agent 模块、权限和接口说明。
- `docs/frontend-api-integration.md`
  - 补充管理员 Agent 列表和详情接口契约。

不修改 Agent 表结构、普通用户 `AgentController`、Chat 模块和 `AgentRuntimeService`。

---

### Task 1: 添加管理员响应模型和服务失败测试

**Files:**
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/vo/AgentAdminVO.java`
- Modify: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentService.java`
- Modify: `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`

- [ ] **Step 1: 创建管理员响应记录。**

创建 `AgentAdminVO`，字段与管理员只读契约完全一致，并在 canonical constructor 中复制列表：

```java
package com.example.aichat.agent.vo;

import java.time.LocalDateTime;
import java.util.List;

public record AgentAdminVO(
        Long id,
        Long userId,
        String name,
        String description,
        String systemPrompt,
        Long modelId,
        List<Long> knowledgeBaseIds,
        List<String> toolCodes,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
    public AgentAdminVO {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
```

- [ ] **Step 2: 扩展服务接口。**

在 `AgentService` 现有 `updateStatus` 方法之后加入：

```java
PageResult<AgentAdminVO> adminPage(int page, int size);

AgentAdminVO adminGet(Long agentId);
```

同时导入 `com.example.aichat.agent.vo.AgentAdminVO`。保留所有现有用户范围方法签名。

- [ ] **Step 3: 为管理员列表和详情写失败测试。**

在 `AgentServiceImplTest` 增加以下 imports（该文件已有 `org.mockito.ArgumentCaptor`，不要重复添加）：

```java
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.common.api.PageResult;

import static org.mockito.Mockito.verifyNoInteractions;
```

在测试类中加入这些测试和辅助方法：

```java
@Test
void adminPageReturnsAgentsAcrossUsersWithAdminView() {
    AgentDefinition newest = existingAgent(101L, 7L);
    newest.setName("Newest");
    newest.setUpdateTime(LocalDateTime.of(2026, 11, 20, 12, 0));
    AgentDefinition older = existingAgent(100L, 8L);
    older.setName("Older");
    older.setUpdateTime(LocalDateTime.of(2026, 11, 19, 12, 0));
    Page<AgentDefinition> page = new Page<>(1, 20);
    page.setRecords(List.of(newest, older));
    page.setTotal(2L);
    when(agentMapper.selectPage(any(), any())).thenReturn(page);
    when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
    when(agentToolMapper.selectList(any())).thenReturn(List.of());

    PageResult<AgentAdminVO> result = service.adminPage(1, 20);

    assertThat(result.total()).isEqualTo(2);
    assertThat(result.records()).extracting(AgentAdminVO::id).containsExactly(101L, 100L);
    assertThat(result.records()).extracting(AgentAdminVO::userId).containsExactly(7L, 8L);
    verifyNoInteractions(modelService, knowledgeBaseRepository, toolQueryService);
}

@Test
void adminPageNormalizesPageAndSize() {
    Page<AgentDefinition> page = new Page<>(1, 100);
    page.setRecords(List.of(existingAgent(101L, 7L)));
    page.setTotal(1L);
    when(agentMapper.selectPage(any(), any())).thenReturn(page);
    when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
    when(agentToolMapper.selectList(any())).thenReturn(List.of());

    PageResult<AgentAdminVO> result = service.adminPage(0, 1000);

    assertThat(result.current()).isEqualTo(1);
    assertThat(result.size()).isEqualTo(100);
    assertThat(result.pages()).isEqualTo(1);
}

@Test
void adminGetReturnsBindingsAndDoesNotValidateResources() {
    AgentDefinition agent = existingAgent(100L, 8L);
    when(agentMapper.selectById(100L)).thenReturn(agent);
    AgentKnowledgeBase baseBinding = bindingBase(11L);
    baseBinding.setId(1L);
    AgentTool toolBinding = bindingTool("order_query");
    toolBinding.setId(2L);
    when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of(baseBinding));
    when(agentToolMapper.selectList(any())).thenReturn(List.of(toolBinding));

    AgentAdminVO result = service.adminGet(100L);

    assertThat(result.userId()).isEqualTo(8L);
    assertThat(result.knowledgeBaseIds()).containsExactly(11L);
    assertThat(result.toolCodes()).containsExactly("order_query");
    verifyNoInteractions(modelService, knowledgeBaseRepository, toolQueryService);
}

@Test
void adminGetTreatsMissingAgentAsNotFound() {
    when(agentMapper.selectById(999L)).thenReturn(null);

    assertThatThrownBy(() -> service.adminGet(999L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_NOT_FOUND);
    verify(agentKnowledgeBaseMapper, never()).selectList(any());
    verify(agentToolMapper, never()).selectList(any());
}
```

- [ ] **Step 4: 运行服务测试确认当前实现缺失。**

Run:

```bash
mvn -pl ai-platform-agent -am -Dtest=AgentServiceImplTest test
```

Expected: 编译失败，提示 `AgentServiceImpl` 尚未实现 `adminPage` 和 `adminGet`。

---

### Task 2: 实现 Agent 服务的管理员查询

**Files:**
- Modify: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentServiceImpl.java`
- Modify: `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`

- [ ] **Step 1: 增加管理员响应导入。**

在 `AgentServiceImpl` 增加以下 imports：

```java
import com.example.aichat.agent.vo.AgentAdminVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
```

- [ ] **Step 2: 实现管理员分页查询。**

在现有 `page` 方法之后加入以下方法。使用 MyBatis-Plus `selectPage` 让数据库负责分页，再对当前页 Agent 的两类绑定关系各执行一次批量查询；`@TableLogic` 会由 MyBatis-Plus 自动追加未删除条件：

```java
@Override
public PageResult<AgentAdminVO> adminPage(int page, int size) {
    int current = Math.max(page, 1);
    int pageSize = Math.min(Math.max(size, 1), 100);
    Page<AgentDefinition> result = agentMapper.selectPage(new Page<>(current, pageSize),
            new LambdaQueryWrapper<AgentDefinition>()
                    .orderByDesc(AgentDefinition::getUpdateTime)
                    .orderByDesc(AgentDefinition::getId));
    List<Long> agentIds = result.getRecords().stream().map(AgentDefinition::getId).toList();
    Map<Long, List<Long>> knowledgeBaseIdsByAgent = loadKnowledgeBaseIds(agentIds);
    Map<Long, List<String>> toolCodesByAgent = loadToolCodes(agentIds);
    return PageResult.from(result, agent -> toAdminView(agent,
            knowledgeBaseIdsByAgent.getOrDefault(agent.getId(), List.of()),
            toolCodesByAgent.getOrDefault(agent.getId(), List.of())));
}
```

- [ ] **Step 3: 实现管理员详情查询。**

在 `adminPage` 之后加入：

```java
@Override
public AgentAdminVO adminGet(Long agentId) {
    AgentDefinition agent = agentMapper.selectById(agentId);
    if (agent == null) {
        throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
    }
    return toAdminView(agent);
}
```

`selectById` 使用 MyBatis-Plus 逻辑删除规则，已删除 Agent 会返回 `null`。

- [ ] **Step 4: 添加管理员响应映射。**

在现有 `toView` 方法之后加入 `toAdminView`，保持与用户详情相同的绑定查询顺序，但增加 `userId`：

```java
private AgentAdminVO toAdminView(AgentDefinition agent) {
    List<AgentKnowledgeBase> knowledgeBindings = agentKnowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<AgentKnowledgeBase>()
                    .eq(AgentKnowledgeBase::getAgentId, agent.getId())
                    .orderByAsc(AgentKnowledgeBase::getId));
    List<AgentTool> toolBindings = agentToolMapper.selectList(
            new LambdaQueryWrapper<AgentTool>()
                    .eq(AgentTool::getAgentId, agent.getId())
                    .orderByAsc(AgentTool::getId));
    return toAdminView(agent,
            knowledgeBindings.stream().map(AgentKnowledgeBase::getKnowledgeBaseId).toList(),
            toolBindings.stream().map(AgentTool::getToolCode).toList());
}

private AgentAdminVO toAdminView(AgentDefinition agent, List<Long> knowledgeBaseIds,
                                 List<String> toolCodes) {
    return new AgentAdminVO(agent.getId(), agent.getUserId(), agent.getName(), agent.getDescription(),
            agent.getSystemPrompt(), agent.getModelId(), knowledgeBaseIds, toolCodes, agent.getStatus(),
            agent.getCreateTime(), agent.getUpdateTime());
}

private Map<Long, List<Long>> loadKnowledgeBaseIds(List<Long> agentIds) {
    if (agentIds.isEmpty()) {
        return Map.of();
    }
    return agentKnowledgeBaseMapper.selectList(new LambdaQueryWrapper<AgentKnowledgeBase>()
                    .in(AgentKnowledgeBase::getAgentId, agentIds)
                    .orderByAsc(AgentKnowledgeBase::getAgentId)
                    .orderByAsc(AgentKnowledgeBase::getId))
            .stream()
            .collect(Collectors.groupingBy(AgentKnowledgeBase::getAgentId, LinkedHashMap::new,
                    Collectors.mapping(AgentKnowledgeBase::getKnowledgeBaseId, Collectors.toList())));
}

private Map<Long, List<String>> loadToolCodes(List<Long> agentIds) {
    if (agentIds.isEmpty()) {
        return Map.of();
    }
    return agentToolMapper.selectList(new LambdaQueryWrapper<AgentTool>()
                    .in(AgentTool::getAgentId, agentIds)
                    .orderByAsc(AgentTool::getAgentId)
                    .orderByAsc(AgentTool::getId))
            .stream()
            .collect(Collectors.groupingBy(AgentTool::getAgentId, LinkedHashMap::new,
                    Collectors.mapping(AgentTool::getToolCode, Collectors.toList())));
}
```

不要修改 `requireOwned`, `page`, `get`, `create`, `update`, `delete` 或 `updateStatus` 的用户范围条件。

- [ ] **Step 5: 运行 Agent 服务测试。**

Run:

```bash
mvn -pl ai-platform-agent -am -Dtest=AgentServiceImplTest test
```

Expected: `AgentServiceImplTest` 全部通过，管理员查询没有调用模型、知识库或工具资源校验服务。

- [ ] **Step 6: 运行 Agent 模块全部测试。**

Run:

```bash
mvn -pl ai-platform-agent -am test
```

Expected: Agent 模块及其依赖模块测试通过。

---

### Task 3: 添加管理员 Controller 和权限测试

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminAgentController.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminAgentControllerTest.java`

- [ ] **Step 1: 写 Controller 失败测试。**

创建 `AdminAgentControllerTest`：

```java
package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.common.api.PageResult;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAgentControllerTest {
    @Mock
    private AgentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAgentController(service)).build();
    }

    @Test
    void pagesAllAgents() throws Exception {
        when(service.adminPage(2, 30)).thenReturn(new PageResult<>(List.of(
                new AgentAdminVO(100L, 7L, "Support", "Support agent", "You are support.", 2L,
                        List.of(11L), List.of("order_query"), 1, null, null)),
                1, 2, 30, 1));

        mockMvc.perform(get("/api/admin/agents")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(100))
                .andExpect(jsonPath("$.data.records[0].userId").value(7));

        verify(service).adminPage(2, 30);
    }

    @Test
    void getsAgentForAdmin() throws Exception {
        when(service.adminGet(100L)).thenReturn(new AgentAdminVO(100L, 7L, "Support", "Support agent",
                "You are support.", 2L, List.of(), List.of(), 1, null, null));

        mockMvc.perform(get("/api/admin/agents/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.userId").value(7));

        verify(service).adminGet(100L);
    }

    @Test
    void protectsBothEndpointsWithAgentReadPermission() throws Exception {
        Method page = AdminAgentController.class.getDeclaredMethod("page", int.class, int.class);
        Method get = AdminAgentController.class.getDeclaredMethod("get", Long.class);

        assertThat(page.getAnnotation(SaCheckPermission.class).value()).isEqualTo("agent:read");
        assertThat(get.getAnnotation(SaCheckPermission.class).value()).isEqualTo("agent:read");
    }
}
```

- [ ] **Step 2: 运行 Controller 测试确认 Controller 缺失。**

Run:

```bash
mvn -pl ai-platform-admin -am -Dtest=AdminAgentControllerTest test
```

Expected: 编译失败，提示 `AdminAgentController` 不存在。

- [ ] **Step 3: 实现管理员 Controller。**

创建 `AdminAgentController`：

```java
package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/agents")
public class AdminAgentController {
    private final AgentService service;

    public AdminAgentController(AgentService service) {
        this.service = service;
    }

    @GetMapping
    @SaCheckPermission("agent:read")
    public ApiResponse<PageResult<AgentAdminVO>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ApiResponse.success(service.adminPage(page, size));
    }

    @GetMapping("/{id}")
    @SaCheckPermission("agent:read")
    public ApiResponse<AgentAdminVO> get(@PathVariable("id") Long id) {
        return ApiResponse.success(service.adminGet(id));
    }
}
```

- [ ] **Step 4: 运行 Admin Controller 测试。**

Run:

```bash
mvn -pl ai-platform-admin -am -Dtest=AdminAgentControllerTest test
```

Expected: `AdminAgentControllerTest` 全部通过，两个接口返回统一 `ApiResponse`，且服务收到正确参数。

---

### Task 4: 注册模块依赖和管理员权限

**Files:**
- Modify: `ai-platform-admin/pom.xml`
- Create: `ai-platform-admin/src/main/resources/db/migration/V16__admin_agent_permission.sql`
- Create: `ai-platform-agent/src/main/resources/db/migration/V17__admin_agent_page_index.sql`

- [ ] **Step 1: 显式添加 Agent 模块依赖。**

在 `ai-platform-admin/pom.xml` 的模块依赖区加入：

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-agent</artifactId>
    <version>${project.version}</version>
</dependency>
```

保留现有 `ai-platform-chat` 依赖；显式依赖用于声明 Admin 直接使用 Agent 服务的模块边界。

- [ ] **Step 2: 添加权限迁移。**

创建 `V16__admin_agent_permission.sql`：

```sql
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Agent read', 'agent:read', 3, 400, 1);
```

不插入 `sys_role_permission` 关系，避免未经授权扩大既有角色权限。管理员角色通过现有角色权限接口分配该权限。

- [ ] **Step 3: 添加管理员分页索引。**

创建 `V17__admin_agent_page_index.sql`：

```sql
alter table agent_definition
    add index idx_agent_admin_page (deleted, update_time, id);
```

该索引覆盖 MyBatis-Plus 逻辑删除条件和管理员列表的更新时间、ID 倒序排序；MySQL 可对升序索引执行反向扫描。

- [ ] **Step 4: 编译 Admin 模块和依赖。**

Run:

```bash
mvn -pl ai-platform-admin -am -DskipTests compile
```

Expected: Agent、Chat、Admin 及其依赖编译成功，且不存在 Admin 到 Agent 的未解析依赖。

---

### Task 5: 更新后端和前端接口文档

**Files:**
- Modify: `docs/backend-development-guide.md`
- Modify: `docs/frontend-api-integration.md`

- [ ] **Step 1: 更新后端模块概览。**

在 `docs/backend-development-guide.md` 的 Admin 模块职责中加入“管理员 Agent 只读查询”，并在 Agent 章节明确：

```text
管理员 Agent 查询由 ai-platform-admin 暴露，权限为 agent:read；
ai-platform-agent 负责查询 AgentDefinition 和两类绑定关系；
管理员列表包含所有未逻辑删除 Agent，普通用户接口仍按 userId 隔离。
```

在数据存储或权限说明处补充 `agent:read` 由 `V16__admin_agent_permission.sql` 创建，且不会自动绑定角色。

- [ ] **Step 2: 更新前端管理员接口契约。**

在 `docs/frontend-api-integration.md` 的管理员知识库接口之后增加管理员 Agent 表格：

```markdown
### 管理员 Agent 接口

管理员 Agent 接口需要 `agent:read` 权限，只读查询全部未逻辑删除的 Agent，包含启用和禁用状态。分页 `page` 默认 1、`size` 默认 20，服务端将 `page` 限制为至少 1，将 `size` 限制在 1–100。

| 方法与路径 | 权限 | 说明 |
| --- | --- | --- |
| `GET /api/admin/agents?page=1&size=20` | `agent:read` | 分页查询全部用户 Agent |
| `GET /api/admin/agents/{id}` | `agent:read` | 查询 Agent 详情和绑定关系 |
```

增加响应示例，明确 `userId` 的含义：

```json
{
  "id": 100,
  "userId": 7,
  "name": "客服助手",
  "description": "回答产品和售后问题",
  "systemPrompt": "你是一个专业、准确的客服助手。",
  "modelId": 3,
  "knowledgeBaseIds": [10, 12],
  "toolCodes": ["order_query"],
  "status": 1,
  "createTime": "2026-11-20T10:00:00",
  "updateTime": "2026-11-20T10:00:00"
}
```

说明：已删除 Agent 不返回；不存在的详情返回 `AGENT_NOT_FOUND`；管理员查看不重新校验绑定资源；响应不包含工具凭据或其他敏感配置。

- [ ] **Step 3: 检查文档差异。**

Run:

```bash
git diff --check -- docs/backend-development-guide.md docs/frontend-api-integration.md
```

Expected: 无空白错误。

---

### Task 6: 全量验证和安全回归

**Files:**
- No additional files.

- [ ] **Step 1: 运行 Agent 和 Admin 定向测试。**

Run:

```bash
mvn -pl ai-platform-agent -am test
mvn -pl ai-platform-admin -am test
```

Expected: 两个模块及其依赖的测试全部通过。

- [ ] **Step 2: 运行全量测试。**

Run:

```bash
mvn test
```

Expected: 所有模块测试通过，普通用户 Agent 和 Chat 回归测试不受影响。

- [ ] **Step 3: 运行生产编译。**

Run:

```bash
mvn -DskipTests compile
```

Expected: 所有模块编译成功。

- [ ] **Step 4: 检查依赖方向和工作区差异。**

Run:

```bash
mvn -q -pl ai-platform-agent dependency:tree -Dincludes=com.example.aichat:ai-platform-admin,com.example.aichat:ai-platform-chat
mvn -q -pl ai-platform-admin dependency:tree -Dincludes=com.example.aichat:ai-platform-agent
git diff --check
git status --short
```

Expected：

- `ai-platform-agent` 不依赖 `ai-platform-admin` 或 `ai-platform-chat`；
- `ai-platform-admin` 显式依赖 `ai-platform-agent`；
- 差异只包含本功能相关文件和原有未跟踪 `.DS_Store`；
- `.DS_Store` 不被加入提交。

- [ ] **Step 5: 完成安全检查。**

确认：

- `AgentController` 的用户接口仍通过 `userId` 查询；
- `AdminAgentController` 只有 `agent:read` 只读接口，没有写操作；
- 管理员响应没有密码、凭据、认证 Header、工具 URL 或完整工具配置；
- 管理员查看不会触发 Agent 资源执行或工具调用；
- 已删除 Agent 不会通过列表或详情暴露；
- `V16__admin_agent_permission.sql` 不会自动给任意角色增加权限；
- `.DS_Store` 不被加入提交。

## 自检清单

- **设计覆盖：** Task 1–2 覆盖 `AgentAdminVO`、服务接口、数据库分页、批量绑定加载、详情、逻辑删除和绑定映射；Task 3 覆盖两个管理员路由、统一响应和权限注解；Task 4 覆盖显式模块依赖、权限迁移与管理员分页索引；Task 5 覆盖两份接口文档；Task 6 覆盖定向、全量、编译、依赖方向和安全回归。
- **占位符检查：** 计划没有 `TODO`、`TBD` 或未确定实现；每个代码变更都给出目标文件和具体内容。
- **类型一致性：** `AgentAdminVO`、`AgentService.adminPage/adminGet`、Controller 返回类型及测试中的泛型保持一致；分页使用现有 `PageResult`；权限码始终为 `agent:read`。
- **兼容性：** 普通用户 `AgentService` 方法、`AgentController`、Agent 运行时和 Chat 模块不改变；新管理员查询仅增加无用户条件的只读路径。
- **边界安全：** Admin 不直接访问 Mapper；Agent 模块不依赖 Admin/Chat；管理员查询只读取已存在的 Agent 配置和绑定关系。
