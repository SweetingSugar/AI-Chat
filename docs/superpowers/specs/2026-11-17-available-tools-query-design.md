# 可用工具查询接口设计

## 目标

新增一个面向已登录用户的工具查询接口，让聊天或其他前端页面能够获取当前可使用的工具列表。接口需要登录，但不区分管理员和普通用户，也不需要任何工具管理或工具执行权限。

## 范围

包含：

- 新增 `GET /api/tools/available`；
- 返回已启用、可用且其 MCP 服务已启用的工具；
- 使用面向用户的安全响应模型，避免暴露 HTTP 地址、认证配置和凭证信息；
- 保持现有 `/api/**` 全局登录校验；
- 增加服务层和控制器测试，并更新接口文档。

不包含：

- 不修改工具创建、编辑、删除、启停或 MCP 发现接口；
- 不改变工具执行权限校验和执行审计；
- 不执行远程 HTTP 请求或 MCP 请求；
- 不新增数据库表或迁移；
- 不允许匿名访问。

## API 契约

### 请求

```http
GET /api/tools/available
Authorization: <登录态请求头>
```

接口位于 `/api/**` 下，因此由现有 Sa-Token 过滤器要求有效登录。控制器方法不添加 `@SaCheckPermission`，已登录用户即使没有管理员权限、`tool:read` 或 `tool:execute` 权限，也可以访问。

### 响应

沿用统一响应包装：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": [
    {
      "toolCode": "weather_current",
      "toolName": "当前天气",
      "description": "查询当前天气",
      "toolType": "HTTP",
      "inputSchema": {
        "type": "object",
        "properties": {
          "city": { "type": "string" }
        },
        "required": ["city"]
      }
    }
  ]
}
```

响应元素使用新的 `AvailableToolView`，只包含：

- `toolCode`
- `toolName`
- `description`
- `toolType`
- `inputSchema`

不返回 `httpConfig`、`authType`、`authHeaderName`、`hasCredential`、`credentialVersion`、`mcpServerId`、状态、可用性、创建时间或更新时间等管理字段。

无可用工具时返回空数组。未登录访问沿用现有 `401 / UNAUTHENTICATED` 响应。

## 可用性规则

查询服务读取未逻辑删除的 `ToolDefinition`，并按工具 ID 升序返回满足下列条件的工具：

1. `status = 1`；
2. `availability = AVAILABLE`；
3. 如果工具类型为 `HTTP`，直接视为满足关联服务条件；
4. 如果工具类型为 `MCP`：
   - `mcpServerId` 必须存在；
   - 关联 MCP 服务必须存在且 `status = 1`。

停用工具、`STALE` 工具、逻辑删除工具、关联 MCP 服务不存在或已停用的工具都不会出现在结果中。查询不因某个不符合条件的工具而失败，也不会触发远程发现或执行。

## 架构与职责

### `ToolQueryService`

新增只读查询服务及实现类，负责组合可用性规则并将实体映射为安全响应模型。它依赖 `ToolDefinitionMapper` 获取工具定义，依赖 `McpServerManagementService` 查询 MCP 服务状态。

查询服务不复用 `ToolManagementService`，因为后者返回包含管理配置的 `ToolManagementView`，同时承担管理写操作；将面向用户的查询职责独立出来可避免响应模型误复用导致敏感字段泄露。

### `AvailableToolView`

新增安全的记录类型，`inputSchema` 使用 `JsonNode`。服务映射工具时解析持久化的 JSON Schema；无效 Schema 按现有业务异常机制报告配置错误，不直接返回原始字符串。

### 控制器

扩展现有 `ToolExecutionController`，在 `/api/tools` 路径下增加 `GET /available` 方法。该方法只调用 `ToolQueryService` 并包装为 `ApiResponse`，不添加 `@SaCheckPermission`。现有 `POST /api/tools/execute` 及其 `tool:execute` 权限保持不变。

## 数据流

```text
已登录请求
    |
    v
Sa-Token 全局登录过滤器
    |
    v
GET /api/tools/available
    |
    v
ToolQueryService
    |
    +--> ToolDefinitionMapper：查询未删除工具
    |
    +--> MCP 工具 -> McpServerManagementService：确认服务存在且启用
    |
    v
AvailableToolView 列表
    |
    v
ApiResponse.success(...)
```

查询过程不解密凭证、不调用 `ToolExecutionService`、不创建工具调用日志，也不访问外部 HTTP/MCP 服务。

## 错误处理

- 未登录：由现有 Sa-Token 过滤器返回 `401 / UNAUTHENTICATED`；
- 数据库中保存的 HTTP 或 MCP Schema 无法解析：沿用工具配置错误的业务异常码；
- MCP 工具关联服务缺失或停用：过滤该工具，不作为请求错误；
- 数据库查询或其他未预期异常：沿用全局异常处理器行为。

## 测试策略

### `ToolQueryService` 单元测试

使用 Mapper 和 MCP 服务管理接口的 mock，覆盖：

1. 返回启用且 `AVAILABLE` 的 HTTP 工具；
2. 排除停用工具、`STALE` 工具和逻辑删除工具；
3. 返回启用且关联 MCP 服务启用的 MCP 工具；
4. 排除缺失 `mcpServerId`、缺失关联服务和停用关联服务的 MCP 工具；
5. 按工具 ID 升序返回；
6. Schema 正确转换为 `JsonNode`，并验证无效 Schema 按业务异常处理；
7. 通过响应类型确认不暴露 URL、认证配置和凭证字段。

### 控制器测试

验证：

- `GET /api/tools/available` 返回 `ApiResponse` 和查询结果；
- 控制器方法未声明 `@SaCheckPermission`；
- 既有执行接口仍保留 `tool:execute` 权限注解。

### 回归验证

至少运行工具模块测试，并运行 Maven 全量测试，确认新增查询服务不会改变工具管理、发现和执行行为。

## 文档变更

更新 `docs/backend-development-guide.md` 和 `docs/frontend-api-integration.md`：

- 记录接口路径、登录要求和“不需要管理员/功能权限”的行为；
- 记录可用性筛选规则；
- 记录返回字段及敏感管理字段不在响应中的约束。
