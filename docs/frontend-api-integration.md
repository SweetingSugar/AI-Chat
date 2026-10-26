# AI Chat 前端接口对接文档

> 本文以当前后端控制器、DTO、VO 与异常处理代码为准，面向前端调用和联调。除登录、注册、登录状态查询外，所有 `/api/**` 接口都需要认证。

## 1. 通用约定

### 1.1 基础地址与认证

- 默认服务地址：`http://localhost:8080`。
- 登录成功后，从响应 `data.tokenName` 和 `data.tokenValue` 取得认证信息；后续请求在 Header 中携带同名 Token。

```http
satoken: <tokenValue>
```

不要把请求头名称写死为 `satoken`：后端可通过 `SA_TOKEN_NAME` 改变它，应始终使用登录响应中的 `tokenName`。

### 1.2 普通响应结构

除两个聊天 SSE 接口外，所有接口返回下列 JSON 包装结构。

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {}
}
```

删除、登出、状态更新、分配角色/权限等无返回实体的成功响应中，`data` 为 `null`。

### 1.3 错误响应

错误同样使用上述结构，HTTP 状态与 `code` 对应如下。

| HTTP 状态 | `code` | 说明 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | DTO 字段校验、服务层枚举/日期/分页规则校验失败 |
| 401 | `UNAUTHENTICATED` | 未携带有效登录态 |
| 401 | `INVALID_CREDENTIALS` | 用户名或密码错误 |
| 403 | `FORBIDDEN` | 账号被禁用或没有接口权限 |
| 404 | `RESOURCE_NOT_FOUND` / `CHAT_CONVERSATION_NOT_FOUND` | 资源、消息或会话不存在，或不属于当前用户 |
| 409 | `RESOURCE_CONFLICT` | 唯一性冲突、供应商仍有关联模型等 |
| 502 | `AI_PROVIDER_ERROR` | AI 供应商调用异常 |
| 500 | `INTERNAL_ERROR` | 未处理的服务端异常 |

`message` 在部分业务异常中是后端具体错误描述；前端应以 `code` 和 HTTP 状态作为稳定的分支依据。当前全局异常处理仅显式处理 DTO 校验和业务异常；请求参数的类型转换失败（例如日期、数字或布尔查询参数格式错误）、格式错误的 JSON、未知 JSON 枚举值或缺失必填查询参数，都可能以 `500 / INTERNAL_ERROR` 返回，不应在前端将其视为稳定的 400 契约。

### 1.4 时间、状态和分页

- `LocalDateTime` 字段（如 `createTime`）按 JSON 字符串接收；`LocalDate` 统计日期按 `YYYY-MM-DD` 接收。
- 用户与通用管理状态：`1` 为启用，`0` 为禁用。
- 会话状态：`ACTIVE`、`ARCHIVED`。
- 消息状态：`PENDING`、`STREAMING`、`COMPLETED`、`FAILED`、`INTERRUPTED`。
- 分页结构：

```json
{
  "records": [],
  "total": 0,
  "current": 1,
  "size": 20,
  "pages": 0
}
```

## 2. 登录与当前用户

### 2.1 注册

`POST /api/auth/register`

无需认证。

```json
{
  "username": "alice_01",
  "password": "password123",
  "nickname": "Alice",
  "email": "alice@example.com",
  "phone": "13800000000"
}
```

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `username` | 是 | 3–50 个字母、数字或下划线 |
| `password` | 是 | 8–72 个字符 |
| `nickname` | 否 | 最长 50 个字符 |
| `email` | 否 | 最长 100 个字符 |
| `phone` | 否 | 最长 20 个字符 |

成功时 `data` 为用户对象：`id`、`username`、`nickname`、`email`、`phone`、`avatar`、`status`、`lastLoginTime`、`createTime`。

### 2.2 登录

`POST /api/auth/login`

无需认证。

`username`、`password` 均必填；二者分别最长 50、72 个字符。

```json
{
  "username": "alice_01",
  "password": "password123"
}
```

成功响应示例：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "tokenName": "satoken",
    "tokenValue": "<token>",
    "user": {
      "id": 1,
      "username": "alice_01",
      "nickname": "Alice",
      "email": "alice@example.com",
      "phone": "13800000000",
      "avatar": null,
      "status": 1,
      "lastLoginTime": "2026-10-19T10:00:00",
      "createTime": "2026-10-19T09:00:00"
    }
  }
}
```

前端应保存 `tokenName` 与 `tokenValue`，并在请求拦截器中添加请求头。修改密码、账号被后台禁用、主动退出登录或退出全部会话后，应清理本地登录态。角色自身或权限、角色权限被后台变更/删除时，后端也会注销受影响用户的登录态；直接重新分配某个用户的角色不会立即注销该用户。

### 2.3 认证相关接口

| 方法与路径 | 认证 | 请求 | `data` |
| --- | --- | --- | --- |
| `POST /api/auth/logout` | 是 | 无 | `null` |
| `POST /api/auth/logout-all` | 是 | 无 | `null` |
| `GET /api/auth/me` | 是 | 无 | 用户对象 |
| `GET /api/auth/status` | 否 | 无 | `{ loggedIn, userId, tokenTimeout }` |
| `POST /api/auth/password` | 是 | `{ oldPassword, newPassword }` | `null` |

修改密码的两个字段均必填；`oldPassword` 最长 72 个字符，`newPassword` 长度为 8–72，且不能与旧密码相同。`/api/auth/status` 未登录时返回 `loggedIn: false`、`userId: null`、`tokenTimeout: 0`。

## 3. 聊天接口

### 3.1 可用模型

`GET /api/chat/models`

返回当前已启用且供应商可用的模型数组，供创建会话或发起对话时选择 `modelId`。响应模型字段为 `id`、`providerId`、`providerName`、`modelCode`、`modelName`、`contextWindow`、`maxOutputTokens`、`supportStream` 和 `defaultModel`。

### 3.2 会话

所有会话接口需要认证，且只能访问当前用户自己的会话。

| 方法与路径 | 请求 | `data` |
| --- | --- | --- |
| `POST /api/chat/sessions` | `{ title?, defaultModelId? }` | 会话对象 |
| `GET /api/chat/sessions?page=1&size=20` | 查询参数；`page` 自动限制为不小于 1，`size` 自动限制为 1–100 | 分页会话对象 |
| `GET /api/chat/sessions/{id}` | 无 | `{ session, messages }` |
| `PUT /api/chat/sessions/{id}` | `{ title?, defaultModelId? }` | 会话对象 |
| `PUT /api/chat/sessions/{id}/pin?pinned=true` | `pinned` 为布尔值 | 会话对象 |
| `DELETE /api/chat/sessions/{id}` | 无 | `null` |

`title` 最长 200 个字符。会话对象字段如下：

```json
{
  "id": 10,
  "title": "关于项目的讨论",
  "defaultModelId": 3,
  "pinned": false,
  "status": "ACTIVE",
  "lastMessageTime": "2026-10-19T10:30:00",
  "createTime": "2026-10-19T10:00:00",
  "updateTime": "2026-10-19T10:30:00"
}
```

会话详情中的 `messages` 是完整消息列表，消息对象字段为：

```json
{
  "id": 101,
  "sessionId": 10,
  "role": "ASSISTANT",
  "content": "你好，有什么可以帮你？",
  "sequence": 2,
  "status": "COMPLETED",
  "generationTaskId": 50,
  "requestId": "8bf0f7e5-0a8f-4f9c-9a32-xxxxxxxxxxxx",
  "modelId": 3,
  "errorCode": null,
  "errorMessage": null,
  "createTime": "2026-10-19T10:30:00",
  "updateTime": "2026-10-19T10:30:03"
}
```

### 3.3 消息分页

`GET /api/chat/sessions/{sessionId}/messages?page=1&size=20&status=COMPLETED`

需要认证。`page` 默认 `1` 且会自动限制为不小于 1，`size` 默认 `20` 且会自动限制为 1–100；`status` 可省略，填写时必须为消息状态枚举且不区分大小写。`data` 为第 1.4 节的分页结构，`records` 元素为消息对象。

### 3.4 发起流式对话

`POST /api/chat/sessions/{sessionId}/stream`

需要认证，响应 `Content-Type` 为 `text/event-stream`。请求体：

```json
{
  "content": "请解释这段代码",
  "modelId": 3,
  "requestId": "client-generated-request-id",
  "temperature": 0.7,
  "topP": 0.9,
  "maxOutputTokens": 2048
}
```

| 字段 | 必填 | 约束 / 说明 |
| --- | --- | --- |
| `content` | 是 | 非空，最长 100000 字符 |
| `modelId` | 否 | 指定本次模型；缺省时使用会话默认模型，再回退到系统默认模型 |
| `requestId` | 否 | 最长 64 个字符；缺省时后端生成 UUID |
| `temperature` | 否 | 0–2 |
| `topP` | 否 | 0–1 |
| `maxOutputTokens` | 否 | 1–100000；当前用于上下文预算，OpenAI 兼容适配器未将该值传入供应商客户端作为输出上限 |

正常建立生成任务后，后端发送 `start`、零到多个 `delta`、`complete` 或 `failed` 事件；同一会话已有生成任务，或生成准备阶段发生异常时，可能直接只发送 `failed`。前一种场景的 SSE `errorCode` 为 `CHAT_GENERATION_IN_PROGRESS`，且 `taskId`、`assistantMessageId` 为 `null`。每个事件的 SSE `id` 均为请求 ID；`data` 结构如下。

| 字段 | `start` | `delta` | `complete` | `failed` |
| --- | --- | --- | --- | --- |
| `type` | `START` | `DELTA` | `COMPLETE` | `FAILED` |
| `taskId` | 有 | 有 | 有 | 可能为 `null` |
| `requestId` | 有 | 有 | 有 | 有 |
| `delta` | `null` | 文本片段 | `null` | `null` |
| `assistantMessageId` | 有 | 有 | 有 | 可能为 `null` |
| `modelId` / `modelCode` | 有 | 有 | 有 | 有 |
| `usage` | `null` | `null` | 通常为 `{ inputTokens, outputTokens, totalTokens, source: "ESTIMATED" }`，也可能为 `null` | `null` |
| `errorCode` / `message` | `null` | `null` | `null` | 有 |

SSE 事件示例：

```text
event: delta
id: 8bf0f7e5-0a8f-4f9c-9a32-xxxxxxxxxxxx
data: {"type":"DELTA","taskId":50,"requestId":"8bf0f7e5-0a8f-4f9c-9a32-xxxxxxxxxxxx","delta":"这段代码","assistantMessageId":101,"modelId":3,"modelCode":"example-model","usage":null,"errorCode":null,"message":null}
```

当前接口为 **POST** 且需要自定义认证 Header；浏览器原生 `EventSource` 无法直接同时满足这两个条件。前端应使用支持流式响应的 `fetch`/`ReadableStream` 或等效 HTTP 客户端读取响应体。

会话、模型、请求参数与归属校验在创建流前失败时，响应为普通 JSON 错误；已建立流后的模型或编排异常通过 `failed` SSE 事件返回。前端在收到 `failed` 或请求被取消后，应以消息列表重新拉取接口中的最终状态为准。

### 3.5 重新生成

`POST /api/chat/sessions/{sessionId}/regenerate`

需要认证，返回 SSE，事件结构与 3.3 完全一致。请求体：

```json
{
  "messageId": 101,
  "modelId": 3,
  "requestId": "retry-request-id",
  "temperature": 0.7,
  "topP": 0.9,
  "maxOutputTokens": 2048
}
```

`messageId` 必填，必须是当前会话中属于当前用户的助手消息；其余字段的取值范围与 3.3 相同。

## 4. 后台用户、角色与权限

本节接口均需认证，并需要表中列出的权限码。用户接口返回的用户对象与 2.1 一致；角色、权限列表与详情均返回实体字段。

| 方法与路径 | 权限 | 请求 |
| --- | --- | --- |
| `GET /api/system/users` | `system:user:read` | 无 |
| `GET /api/system/users/{id}` | `system:user:read` | 无 |
| `PUT /api/system/users/{id}` | `system:user:update` | `UserUpdateRequest` |
| `PATCH /api/system/users/{id}/status` | `system:user:update` | `{ status: 0 \| 1 }` |
| `PUT /api/system/users/{id}/roles` | `system:user:assign-role` | `{ ids: [1, 2] }` |
| `GET /api/system/roles` | `system:role:read` | 无 |
| `GET /api/system/roles/{id}` | `system:role:read` | 无 |
| `POST /api/system/roles` | `system:role:create` | `RoleRequest` |
| `PUT /api/system/roles/{id}` | `system:role:update` | `RoleRequest` |
| `DELETE /api/system/roles/{id}` | `system:role:delete` | 无 |
| `PUT /api/system/roles/{id}/permissions` | `system:role:assign-permission` | `{ ids: [1, 2] }` |
| `GET /api/system/permissions` | `system:permission:read` | 无 |
| `GET /api/system/permissions/{id}` | `system:permission:read` | 无 |
| `POST /api/system/permissions` | `system:permission:create` | `PermissionRequest` |
| `PUT /api/system/permissions/{id}` | `system:permission:update` | `PermissionRequest` |
| `DELETE /api/system/permissions/{id}` | `system:permission:delete` | 无 |

`UserUpdateRequest`：

```json
{
  "nickname": "Alice",
  "email": "alice@example.com",
  "phone": "13800000000",
  "avatar": "https://example.com/avatar.png"
}
```

字段上限依次为 `nickname` 50、`email` 100、`phone` 20、`avatar` 500；字段均可省略。

`RoleRequest`：

```json
{
  "roleName": "运营人员",
  "roleCode": "operator",
  "description": "运营后台角色",
  "sort": 10,
  "status": 1
}
```

`roleName`、`roleCode` 必填且最长 50；`description` 最长 255；`sort`、`status` 可省略。角色响应字段为 `id`、`roleName`、`roleCode`、`description`、`sort`、`status`、`createTime`、`updateTime`。

`PermissionRequest`：

```json
{
  "parentId": 0,
  "permissionName": "用户查看",
  "permissionCode": "system:user:read",
  "permissionType": 3,
  "path": null,
  "component": null,
  "icon": null,
  "sort": 0,
  "status": 1
}
```

`permissionName` 与 `permissionType` 必填；名称与权限码最长 100，`path` 与 `component` 最长 255，`icon` 最长 100。权限响应字段与请求字段相同，另含 `id`、`createTime`、`updateTime`。

用户角色和角色权限分配请求中的 `ids` 必填，数组内每个 ID 都必须存在。两类分配均为**全量替换**：传入 `[]` 会清空当前全部关联，而不是追加关系。角色权限分配会注销拥有该角色的用户；用户角色分配当前不会立即注销被分配用户。

## 5. 后台 AI 供应商与模型

本节接口均需认证。列表接口返回数组，详情、新增、修改返回单个对象，删除返回 `data: null`。

| 方法与路径 | 权限 | 请求 / `data` |
| --- | --- | --- |
| `GET /api/admin/ai/providers` | `ai:provider:read` | 供应商数组 |
| `GET /api/admin/ai/providers/{id}` | `ai:provider:read` | 单个供应商 |
| `POST /api/admin/ai/providers` | `ai:provider:create` | `ProviderRequest` |
| `PUT /api/admin/ai/providers/{id}` | `ai:provider:update` | `ProviderRequest` |
| `DELETE /api/admin/ai/providers/{id}` | `ai:provider:delete` | `null` |
| `GET /api/admin/ai/models` | `ai:model:read` | 模型数组 |
| `GET /api/admin/ai/models/{id}` | `ai:model:read` | 单个模型 |
| `POST /api/admin/ai/models` | `ai:model:create` | `ModelRequest` |
| `PUT /api/admin/ai/models/{id}` | `ai:model:update` | `ModelRequest` |
| `DELETE /api/admin/ai/models/{id}` | `ai:model:delete` | `null` |

`ProviderRequest`：

```json
{
  "providerCode": "deepseek",
  "providerName": "DeepSeek",
  "providerType": "DEEPSEEK",
  "baseUrl": "https://api.deepseek.com",
  "apiKey": "sk-...",
  "connectTimeout": 10,
  "readTimeout": 60,
  "status": 1,
  "sort": 0,
  "remark": ""
}
```

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `providerCode` | 是 | 最长 50，唯一 |
| `providerName` | 是 | 最长 100 |
| `providerType` | 是 | `OPENAI`、`DEEPSEEK`、`QWEN`、`OLLAMA` |
| `baseUrl` | 是 | 最长 500 |
| `apiKey` | 非 Ollama 供应商新增时是；Ollama 可省略 | 最长 4096；更新时为空或缺省则保留已有 API Key |
| `connectTimeout` | 否 | 1–120；当前 OpenAI 兼容适配器未将该字段传入模型客户端 |
| `readTimeout` | 否 | 1–600；当前 OpenAI 兼容适配器按毫秒传入模型客户端 |
| `status` | 否 | `0` 或 `1`，缺省为 `1` |
| `sort` | 否 | 非负整数，缺省为 `0` |
| `remark` | 否 | 最长 500 |

`OLLAMA` 供应商通常使用 `http://localhost:11434` 作为 `baseUrl`，`apiKey` 可以省略。

供应商响应不返回 API Key，字段为 `id`、`providerCode`、`providerName`、`providerType`、`baseUrl`、`connectTimeout`、`readTimeout`、`status`、`hasApiKey`、`credentialVersion`、`sort`、`remark`。

`ModelRequest`：

```json
{
  "providerId": 1,
  "modelCode": "deepseek-chat",
  "modelName": "DeepSeek Chat",
  "modelType": "chat",
  "contextWindow": 65536,
  "maxOutputTokens": 4096,
  "temperature": 0.7,
  "topP": 0.9,
  "supportStream": 1,
  "isDefault": 1,
  "status": 1,
  "sort": 0,
  "remark": ""
}
```

`providerId`、`modelCode`、`modelName`、`modelType` 必填；`modelCode`、`modelName` 最长 100，`modelType` 最长 32，`remark` 最长 500。模型代码在同一供应商下唯一。`contextWindow`、`maxOutputTokens` 为正整数；`temperature` 为 0–2；`topP` 为 0–1；`supportStream`、`isDefault`、`status` 为 `0` 或 `1`；`sort` 为非负整数。启用模型时其供应商也必须启用。设置 `isDefault: 1` 会清除其他模型的默认标记。可选字段的默认值分别是：上下文窗口 8192、最大输出 Token 1024、流式支持 1、默认模型 0、状态 1、排序 0。

模型响应字段为 `id`、`providerId`、`providerName`、`modelCode`、`modelName`、`modelType`、`contextWindow`、`maxOutputTokens`、`temperature`、`topP`、`supportStream`、`defaultModel`、`configVersion`、`status`、`sort`、`remark`。其中 `supportStream` 与 `defaultModel` 为布尔值。

## 6. 后台统计

统计接口均需认证、都要求日期参数 `from` 与 `to`，并需要各自权限。日期格式为 `YYYY-MM-DD`，查询区间为 `[from, to)`；`to` 必须晚于 `from`，最大跨度 365 天。

| 方法与路径 | 权限 | `data` |
| --- | --- | --- |
| `GET /api/admin/dashboard/overview?from=2026-10-01&to=2026-10-20` | `admin:dashboard:read` | 看板概览对象 |
| `GET /api/admin/dashboard/trend?from=...&to=...` | `admin:dashboard:read` | 看板日趋势数组 |
| `GET /api/admin/statistics/users?from=...&to=...` | `admin:statistics:user:read` | 用户统计对象 |
| `GET /api/admin/statistics/chat?from=...&to=...` | `admin:statistics:chat:read` | 聊天统计对象 |
| `GET /api/admin/statistics/tokens?from=...&to=...&groupBy=DAY&page=1&size=20` | `admin:statistics:token:read` | Token 统计对象 |

字段定义：

| 响应 | 字段 |
| --- | --- |
| 看板概览 | `totalUsers`、`enabledUsers`、`disabledUsers`、`newUsers`、`activeUsers`、`totalSessions`、`newSessions`、`totalMessages`、`userMessages`、`assistantMessages`、`aiCalls`、`successfulAiCalls`、`failedAiCalls`、`aiSuccessRate`、`inputTokens`、`outputTokens`、`totalTokens` |
| 看板日趋势数组元素 | `date`、`newUsers`、`activeUsers`、`newSessions`、`messages`、`aiCalls`、`successfulAiCalls`、`totalTokens` |
| 用户统计 | `totalUsers`、`enabledUsers`、`disabledUsers`、`newUsers`、`activeUsers`、`trend`；`trend` 元素为 `date`、`newUsers`、`activeUsers` |
| 聊天统计 | `totalSessions`、`newSessions`、`totalMessages`、`userMessages`、`assistantMessages`、`completedMessages`、`failedMessages`、`generationTasks`、`successfulGenerationTasks`、`failedGenerationTasks`、`interruptedGenerationTasks`、`generationSuccessRate`、`trend` |
| 聊天统计 `trend` 元素 | `date`、`newSessions`、`messages`、`userMessages`、`assistantMessages`、`completedMessages`、`failedMessages`、`generationTasks`、`successfulGenerationTasks` |
| Token 统计 | `calls`、`successfulCalls`、`failedCalls`、`cancelledCalls`、`inputTokens`、`outputTokens`、`totalTokens`、`estimatedTokens`、`averageDurationMs`、`groups` |
| Token 分组元素 | `key`、`name`、`calls`、`inputTokens`、`outputTokens`、`totalTokens`、`estimated` |

`totalUsers`、`enabledUsers`、`disabledUsers` 与 `totalSessions` 为全部未逻辑删除记录；其余新增、活跃、消息、生成与 AI 调用指标按日期区间计算。活跃用户指区间内发送过 `USER` 消息的去重用户。`aiSuccessRate` 为 `successfulAiCalls / aiCalls`，`generationSuccessRate` 为 `successfulGenerationTasks / (successfulGenerationTasks + failedGenerationTasks + interruptedGenerationTasks)`；二者均为 0–1 的小数并保留两位小数，分母为 0 时返回 0。

`groupBy` 可选值为 `DAY`、`MODEL`、`PROVIDER`、`USER`，缺省为 `DAY`，不区分大小写；`page` 默认 1，`size` 默认 20 且范围为 1–100，超出范围会直接报错。分页仅作用于 `groups` 数组；顶部汇总指标始终统计整个日期区间。`DAY` 分组仅返回有调用记录的日期并按日期升序，`MODEL`、`PROVIDER`、`USER` 分组按总 Token 降序。看板、用户和聊天的日趋势会为无数据日期补零，不能与 Token 的按日分组直接按数组下标对齐。Token 分组响应不提供 `total` 或 `pages`。

## 7. 前端联调顺序

1. 调用登录接口，保存 `tokenName` 与 `tokenValue`。
2. 在所有受保护请求上携带该 Token Header。
3. 创建会话，使用返回的 `id` 拉取详情或建立流式对话。
4. 对话期间消费 `start`、`delta`、`complete` / `failed` 事件；以 `assistantMessageId` 定位前端暂存消息。
5. 流结束后按需重新查询消息分页或会话详情，获得持久化后的最终消息状态。
6. 进入后台页面前，按当前登录用户实际权限处理 403 响应；后台管理接口不返回权限集合，前端可通过权限配置或接口结果控制页面入口。
