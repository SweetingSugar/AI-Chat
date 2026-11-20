# 管理员查看所有 Agent 设计

## 1. 目标

新增管理员只读能力，使拥有 `agent:read` 权限的管理员可以分页查看所有用户的 Agent，并按 Agent ID 查看详情。普通用户现有的 Agent 管理接口和用户归属边界保持不变。

## 2. 范围

包含：

- 新增管理员 Agent 分页列表接口；
- 新增管理员 Agent 详情接口；
- 返回 Agent 所属用户 ID、配置和绑定资源；
- 新增 `agent:read` 权限种子；
- 在 Agent 模块中维护管理员查询逻辑，在 Admin 模块中维护路由和权限注解；
- 增加服务层、控制器测试及接口文档。

不包含：

- 管理员创建、编辑、启停或删除其他用户的 Agent；
- 修改普通用户 `/api/agents` 的行为；
- 修改 Agent 运行时解析、聊天链路或资源校验；
- 返回用户密码、工具凭据、认证 Header、工具 URL 等敏感信息；
- 自动将新权限绑定到既有角色。

## 3. 已确认的业务规则

### 3.1 管理员接口

```text
GET /api/admin/agents
GET /api/admin/agents/{id}
```

两个接口均使用 `@SaCheckPermission("agent:read")`。请求必须经过登录和 Sa-Token 权限校验。

### 3.2 查询范围

- 列表查询所有用户的未逻辑删除 Agent；
- 列表包含启用和禁用状态；
- 已逻辑删除 Agent 不出现在列表，也不能通过详情查询；
- 列表按 `update_time DESC, id DESC` 排序；
- 详情按 Agent ID 查询，不要求传入用户 ID；
- 找不到 Agent 或 Agent 已逻辑删除时，统一抛出现有 `AGENT_NOT_FOUND` 错误；
- 绑定知识库和工具按绑定记录 ID 升序返回，以保证顺序稳定；
- 管理员查看配置时不重新验证模型、知识库或工具是否可用，失效绑定原样返回，便于排查配置问题。

### 3.3 分页

- 列表默认 `page=1`、`size=20`；
- 服务层将页码归一化为至少 1；
- 服务层将页大小限制在 1–100；
- 使用现有 `PageResult` 响应格式。

## 4. 模块边界

```text
ai-platform-admin -> ai-platform-agent
```

`ai-platform-agent` 继续持有 Agent 实体、绑定实体和查询规则。`ai-platform-admin` 不直接注入 Agent Mapper，也不直接操作 Agent 表，只负责控制器路由和管理员权限。

`ai-platform-admin/pom.xml` 显式声明 `ai-platform-agent` 依赖，避免依赖 `ai-platform-chat` 的传递关系隐藏模块边界。

## 5. 服务与响应设计

### 5.1 Agent 服务接口

在现有 `AgentService` 中新增：

```java
PageResult<AgentAdminVO> adminPage(int page, int size);

AgentAdminVO adminGet(Long agentId);
```

现有用户范围方法保持不变，仍然要求 `userId` 并按用户过滤。

### 5.2 管理员响应对象

新增 `AgentAdminVO`，字段为：

```text
id
userId
name
description
systemPrompt
modelId
knowledgeBaseIds
toolCodes
status
createTime
updateTime
```

`knowledgeBaseIds` 和 `toolCodes` 使用不可变列表；响应不包含任何密码、凭据、认证 Header、工具 URL 或工具完整配置。

### 5.3 控制器

新增 `AdminAgentController`：

```java
@RestController
@RequestMapping("/api/admin/agents")
```

方法契约：

```java
@GetMapping
@SaCheckPermission("agent:read")
ApiResponse<PageResult<AgentAdminVO>> page(
        @RequestParam(name = "page", defaultValue = "1") int page,
        @RequestParam(name = "size", defaultValue = "20") int size)

@GetMapping("/{id}")
@SaCheckPermission("agent:read")
ApiResponse<AgentAdminVO> get(@PathVariable("id") Long id)
```

控制器只调用 `AgentService.adminPage` 和 `AgentService.adminGet`，不接收或拼接用户归属条件。

## 6. 数据流

### 6.1 管理员列表

1. Sa-Token 校验当前会话具有 `agent:read`；
2. `AdminAgentController` 接收并转发分页参数；
3. `AgentServiceImpl.adminPage` 查询未逻辑删除的 `AgentDefinition`；
4. 按更新时间和 ID 倒序排序并归一化分页参数；
5. 对当前页 Agent 读取两类绑定关系；
6. 映射为 `AgentAdminVO`，附带 `userId`；
7. 返回 `ApiResponse<PageResult<AgentAdminVO>>`。

### 6.2 管理员详情

1. Sa-Token 校验 `agent:read`；
2. `AgentServiceImpl.adminGet` 按 Agent ID 查询；
3. MyBatis-Plus 逻辑删除条件自动排除已删除记录；
4. 查询知识库和工具绑定关系；
5. 映射为 `AgentAdminVO` 并返回。

管理员查询路径不进入 `AgentResourceValidator`，也不调用运行时资源解析接口。

## 7. 权限与数据库迁移

新增：

```text
ai-platform-admin/src/main/resources/db/migration/V16__admin_agent_permission.sql
```

迁移内容插入：

```text
permission_name = Agent read
permission_code = agent:read
permission_type = 3
sort = 400
status = 1
```

使用 `insert ignore` 保持迁移可重复执行。迁移只创建权限，不自动修改 `sys_role_permission`；管理员角色的绑定由现有角色管理流程完成。

新增 `ai-platform-agent` 的 `V17__admin_agent_page_index.sql`，为 `agent_definition` 增加 `(deleted, update_time, id)` 复合索引，使管理员的逻辑删除过滤和排序分页可以使用数据库索引。

## 8. 错误处理与安全

- 认证和权限失败继续由 Sa-Token 和现有全局异常处理器处理；
- Agent 不存在或已删除时复用 `ErrorCode.AGENT_NOT_FOUND`；
- 不新增管理员专用错误码；
- 管理员查询不会改变普通用户的 `(userId, agentId)` 查询条件；
- 查询响应不暴露工具的敏感配置；
- 不记录或返回凭据、完整工具参数和完整工具响应。

## 9. 测试策略

### 9.1 Agent 服务测试

新增管理员查询相关测试，至少覆盖：

- 管理员列表可同时返回不同用户的 Agent；
- 列表包含启用和禁用 Agent；
- 已逻辑删除 Agent 不被返回；
- 列表按更新时间和 ID 倒序；
- 分页页码和页大小按服务规则归一化；
- 绑定知识库 ID 和工具 code 按绑定 ID 升序返回；
- 管理员详情可按 ID 返回 Agent 及其绑定关系；
- 不存在 Agent 返回 `AGENT_NOT_FOUND`；
- 管理员查询不触发资源可用性校验。

### 9.2 Admin Controller 测试

新增 `AdminAgentControllerTest`，至少覆盖：

- `GET /api/admin/agents` 返回统一成功响应并传递分页参数；
- `GET /api/admin/agents/{id}` 返回统一成功响应并传递 Agent ID；
- 两个 Controller 方法都声明 `@SaCheckPermission("agent:read")`；
- Controller 调用的是管理员查询方法，而不是用户范围查询方法。

### 9.3 回归验证

```bash
mvn -pl ai-platform-admin -am test
mvn -pl ai-platform-agent -am test
mvn test
mvn -DskipTests compile
git diff --check
```

同时确认：

- `ai-platform-agent` 不依赖 `ai-platform-admin` 或 `ai-platform-chat`；
- 普通用户 `/api/agents` 的查询仍然带 `userId`；
- Agent 聊天和运行时资源校验没有行为变化；
- `.DS_Store` 不被加入提交。

## 10. 文件变更清单

新增：

- `ai-platform-agent/src/main/java/com/example/aichat/agent/vo/AgentAdminVO.java`
- `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminAgentController.java`
- `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/AdminAgentControllerTest.java`
- `ai-platform-admin/src/main/resources/db/migration/V16__admin_agent_permission.sql`

修改：

- `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentService.java`
- `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentServiceImpl.java`
- `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`
- `ai-platform-admin/pom.xml`
- `docs/backend-development-guide.md`
- `docs/frontend-api-integration.md`

不修改：

- 普通用户 Agent 接口的 URL、参数和权限边界；
- Chat 模块和 Agent 运行时接口；
- Agent 数据表结构。
