# Tool 模块设计

## 背景

当前项目按 Maven 模块划分领域能力，尚未提供统一的外部工具调用能力。本次新增 `ai-platform-tool`，支持配置和执行 HTTP Tool、连接 Streamable HTTP MCP Server、发现 MCP 工具，并通过统一的 `toolCode` 对外提供执行入口。

## 目标与范围

本次 P0 包含：

- 工具新增、修改、删除、查询和启用/禁用；
- HTTP Tool 的 URL、方法、Header、Query 参数和 JSON Body 模板配置与执行；
- MCP Server 地址、Bearer/API Key/自定义 Header 认证配置与启用/禁用；
- 通过 Streamable HTTP 连接 MCP Server 并发现工具；
- 持久化 MCP 工具发现快照；
- 调用指定 MCP Server 中的 MCP Tool；
- 通过 `toolCode` 统一执行 HTTP 或 MCP Tool；
- 记录工具、调用者、状态、耗时、错误码和错误信息等基础执行日志；
- 管理接口、执行接口、权限、Flyway 迁移和自动化测试。

不在本次范围内：

- 聊天模型自动 Tool Calling；
- MCP stdio 或旧版 SSE 传输；
- OAuth2 授权和刷新令牌；
- 文件上传型 HTTP Tool、multipart、原始二进制请求；
- 自动重试和长期 MCP 会话池；
- 完整请求参数、响应内容或认证信息日志；
- 前端管理页面。

## 方案选择

采用“独立运行时模块 + 管理模块控制器”的方案：

- `ai-platform-tool` 负责实体、Mapper、HTTP/MCP 执行器、发现、统一执行和日志服务；
- `ai-platform-admin` 负责工具和 MCP Server 的管理 REST API、权限注解及日志查询 API；
- `ai-platform-tool` 不依赖 `ai-platform-admin`，避免运行时调用反向依赖管理模块；
- HTTP 和 MCP 均实现统一的 `ToolExecutor` 接口，`ToolExecutionService` 根据工具类型路由；
- HTTP 请求使用 Spring `RestClient`；MCP 使用官方 Java MCP SDK 的 Streamable HTTP 客户端（采用与 Java 17/Spring Boot 3.5 兼容的 `io.modelcontextprotocol.sdk:mcp` 版本，当前实现基线为 `0.18.3`）；
- MCP 连接采用短生命周期会话，发现或调用完成后关闭，不在 P0 引入跨请求连接缓存。

模块关系：

```text
ai-platform-boot
  └─ ai-platform-tool
  └─ ai-platform-admin

ai-platform-admin
  ├─ ToolController
  ├─ ToolMcpServerController
  └─ ToolInvocationController

ai-platform-tool
  ├─ ToolManagementService
  ├─ ToolDiscoveryService
  ├─ ToolExecutionService
  ├─ HttpToolExecutor
  ├─ McpToolExecutor
  └─ ToolInvocationLogService
```

## 数据模型

所有表使用默认 MySQL 数据源，迁移脚本放在 `ai-platform-tool/src/main/resources/db/migration`。建议使用全局未占用的 `V13__tool_schema.sql` 创建工具表，使用 `V14__tool_permission.sql` 注册权限。

### `tool_mcp_server`

保存 MCP Server 连接配置：

- `id`；
- `server_code`，全局唯一；
- `server_name`；
- `endpoint_url`；
- `auth_type`：`NONE`、`BEARER`、`API_KEY`；
- `auth_header_name`；
- `credential_ciphertext`；
- `secret_headers_ciphertext`；
- `connect_timeout`、`read_timeout`；
- `status`：启用/禁用；
- `deleted`；
- `last_discovery_time`、`last_discovery_status`、`last_discovery_error`；
- `create_time`、`update_time`。

凭证和自定义 Header 中的敏感值使用现有 AES-256-GCM 凭证加密模式保存。管理响应只返回认证类型和是否配置凭证，不返回明文或密文。

### `tool_definition`

保存手工配置的 HTTP Tool 和 MCP 工具发现快照：

- `id`；
- `tool_code`，全局唯一；
- `tool_name`、`description`；
- `tool_type`：`HTTP`、`MCP`；
- `source`：`MANUAL`、`DISCOVERED`；
- `status`：启用/禁用；
- `availability`：`AVAILABLE`、`STALE`；
- `mcp_server_id`，MCP Tool 必填；
- `remote_tool_name`，MCP Tool 的远端名称；
- `http_config`，HTTP Tool 的结构化 JSON 配置；
- `auth_type`、`auth_header_name`、`credential_ciphertext`；
- `secret_headers_ciphertext`，敏感 Header 的加密值；
- `input_schema`，MCP Tool 的 JSON Schema；
- `deleted`；
- `create_time`、`update_time`。

HTTP 配置结构如下，普通 Header 可明文保存，敏感 Header 的值单独加密：

```json
{
  "url": "https://example.com/api/orders",
  "method": "POST",
  "headers": {
    "Accept": "application/json"
  },
  "queryParams": {
    "tenant": "demo"
  },
  "bodyTemplate": {
    "orderId": "{{orderId}}"
  }
}
```

HTTP Tool 的认证类型、认证 Header 名称和认证凭证沿用与 MCP Server 相同的 Bearer/API Key/自定义 Header 语义。

MCP 发现使用 `(mcp_server_id, remote_tool_name)` 进行 upsert：

- 已有工具更新名称、描述和 `input_schema`；
- 新工具默认禁用，由管理员显式启用；
- `toolCode` 首次生成后持久化，重新发现时保持稳定；
- 本次发现未返回的旧工具标记为 `STALE`，不物理删除；
- 重新发现不覆盖管理员已经设置的启用/禁用状态；
- `STALE` 工具不可执行，重新发现后恢复 `AVAILABLE`；
- 删除使用逻辑删除或失效状态，保留历史日志关联。

### `tool_invocation_log`

保存基础执行审计：

- `id`；
- `request_id`；
- `tool_id`、`tool_code`、`tool_type`；
- `user_id`；
- `status`：`SUCCESS`、`FAILED`；
- `duration_ms`；
- `error_code`、`error_message`；
- `start_time`、`end_time`、`create_time`。

不保存完整请求参数、响应体、认证 Header 或远端异常堆栈。错误信息写入日志前进行长度限制和敏感信息过滤。

## API 设计

### 统一执行

```http
POST /api/tools/execute
权限：tool:execute
```

请求：

```json
{
  "toolCode": "weather_query",
  "arguments": {
    "city": "Shanghai",
    "unit": "celsius"
  },
  "requestId": "optional-client-request-id"
}
```

`toolCode` 必须对应已启用、未删除且 `AVAILABLE` 的工具；`arguments` 必须是 JSON Object。未传 `requestId` 时由服务端生成 UUID。HTTP Tool 校验模板所需参数，MCP Tool 按发现快照中的 `input_schema` 校验。

成功响应统一为：

```json
{
  "requestId": "8bf0f7e5-...",
  "toolCode": "weather_query",
  "toolType": "HTTP",
  "success": true,
  "statusCode": 200,
  "data": {
    "temperature": 22
  },
  "durationMs": 184
}
```

响应内容可以返回给调用方，但不写入执行日志。失败使用统一业务错误码，并保证不返回凭证或内部堆栈。

### 工具管理

```text
GET    /api/admin/tools
GET    /api/admin/tools/{id}
POST   /api/admin/tools
PUT    /api/admin/tools/{id}
DELETE /api/admin/tools/{id}
PATCH  /api/admin/tools/{id}/status
```

权限分别为 `tool:read`、`tool:create`、`tool:update`、`tool:delete`。MCP 发现快照只允许更新展示字段和状态，不能通过普通 HTTP Tool 配置请求覆盖远端名称或 Schema。

### MCP Server 管理与发现

```text
GET    /api/admin/tool/mcp-servers
GET    /api/admin/tool/mcp-servers/{id}
POST   /api/admin/tool/mcp-servers
PUT    /api/admin/tool/mcp-servers/{id}
DELETE /api/admin/tool/mcp-servers/{id}
PATCH  /api/admin/tool/mcp-servers/{id}/status
POST   /api/admin/tool/mcp-servers/{id}/discover
```

发现接口需要 `tool:mcp-server:discover`。发现成功返回本次同步统计和工具快照；发现失败保留旧快照，只更新 Server 的失败状态和错误信息。禁用 Server 时，关联工具不可执行。

### 执行日志查询

```http
GET /api/admin/tool/invocation-logs?toolId=&status=&page=&size=
权限：tool:invocation:read
```

列表仅返回基础审计字段，不返回请求参数、响应内容和任何认证信息。

## 执行链路

### HTTP Tool

1. 读取 HTTP 配置，解析 URL、方法、Query、Header 和 JSON Body 模板；
2. 使用 `arguments` 替换模板变量；
3. 合并认证 Header 与普通 Header，认证 Header 不允许被调用参数覆盖；
4. 使用 `RestClient` 发起请求；
5. 解析状态码、Content-Type 和 JSON/文本响应；
6. 应用连接超时、读取超时、最大 Body 和最大响应体限制；
7. 无论成功或失败都更新一条执行日志。

模板变量只支持明确的参数路径替换，不执行 SpEL、JavaScript 或其他动态代码。

### MCP Server 发现

```text
读取启用的 Server 配置
  → 建立 Streamable HTTP 会话
  → initialize
  → tools/list
  → 校验工具定义
  → 按 serverId + remoteToolName upsert 快照
  → 标记未返回的旧工具为 STALE
  → 关闭会话
```

发现结果中的工具名、描述和 Schema 按不可信外部数据处理，只用于管理展示和参数校验，不执行 Schema 中的扩展字段或动态内容。

### MCP Tool 调用

1. 校验 Server、Tool 均未删除且启用，Tool 不是 `STALE`；
2. 建立短生命周期 Streamable HTTP MCP 会话；
3. 使用配置的认证信息初始化 MCP 客户端；
4. 调用 `tools/call`，传入远端工具名和 `arguments`；
5. 将 MCP `content`、`structuredContent` 和 `isError` 转换为统一结果；
6. 关闭会话并写入日志。

## 错误和安全边界

稳定错误码包括：

- `TOOL_NOT_FOUND`、`TOOL_DISABLED`、`TOOL_STALE`；
- `TOOL_ARGUMENT_INVALID`、`TOOL_CONFIG_INVALID`；
- `TOOL_CONNECT_TIMEOUT`、`TOOL_READ_TIMEOUT`；
- `TOOL_REMOTE_ERROR`、`TOOL_DISCOVERY_FAILED`、`TOOL_EXECUTION_FAILED`。

管理配置错误沿用 `VALIDATION_ERROR`，资源不存在和关联冲突沿用 `RESOURCE_NOT_FOUND`、`RESOURCE_CONFLICT`。

安全约束：

- URL 只允许 `http`/`https`，保存和执行时都校验；
- 默认拒绝 loopback、link-local、私有网段和云元数据地址，防止 SSRF；受控内网访问只能通过应用级网络策略显式放行；
- 调用参数不能修改 URL、请求方法或认证 Header；
- 限制 Header 数量、参数大小、Body 大小和响应体大小；
- 凭证使用 AES-256-GCM 加密，异常、日志和管理响应不输出明文；
- MCP 返回内容按不可信外部数据处理；
- 不自动重试，避免非幂等 HTTP 或 MCP 工具被重复执行；
- 执行日志在执行前创建、结束后更新；日志写入失败不能掩盖原始执行结果；
- 配置更新、MCP 快照 upsert 和 stale 标记在事务中完成；发现失败不修改旧快照。

## 权限迁移

`V14__tool_permission.sql` 注册以下接口权限：

- `tool:read`、`tool:create`、`tool:update`、`tool:delete`、`tool:execute`；
- `tool:mcp-server:read`、`tool:mcp-server:create`、`tool:mcp-server:update`、`tool:mcp-server:delete`、`tool:mcp-server:discover`；
- `tool:invocation:read`。

权限名称、父级和排序沿用现有系统权限表约定，不修改既有权限编码。

## 测试策略

### `ai-platform-tool`

- `HttpToolExecutorTest`：GET/POST、Query、JSON Body、Header 合并、参数缺失、非法 URL、超时、非 2xx 响应、SSRF 拒绝和响应大小限制；
- `McpToolExecutorTest`：Streamable HTTP 初始化、`tools/list`、`tools/call`、Schema 校验、`isError`、结构化结果和连接失败；
- `ToolDiscoveryServiceTest`：新增/更新快照、稳定 `toolCode`、stale 标记和失败保留旧快照；
- `ToolExecutionServiceTest`：按类型路由、禁用/过期拒绝、成功/失败日志、request ID 生成和透传；
- `ToolManagementServiceTest`：编码唯一性、删除冲突、状态变更和凭证更新策略；
- Mapper/XML 测试：表字段、逻辑删除条件、稳定排序、索引和分页条件。

### `ai-platform-admin`

控制器测试覆盖管理、发现、日志查询路径，验证权限注解、请求校验、参数转发和统一 `ApiResponse` 结构。

### 集成验证

- 在 MySQL 测试数据库执行 Flyway 迁移；
- 使用本地 mock HTTP Server 验证真实 `RestClient` 请求；
- 使用 MCP 协议 mock endpoint 验证发现和调用；
- 运行 tool、admin 及聚合 Maven 测试，确认新模块被启动模块加载。

不依赖真实第三方服务，不将密钥或完整请求/响应写入测试输出。

## 文档更新

实现完成后更新：

- `docs/backend-development-guide.md`：增加 tool 模块、数据表、执行链路和配置说明；
- `docs/frontend-api-integration.md`：增加工具管理、MCP Server、统一执行和日志查询接口；
- 如 MCP SDK 或执行超时增加应用配置，在 `ai-platform-boot/src/main/resources/application.yml` 及配置表中补充环境变量说明。
