# AI Chat 后端已实现功能说明

> 本文根据当前仓库代码与 Flyway 迁移脚本整理，仅描述已经实现的后端能力，供新成员定位模块、接口和关键调用链使用。

## 1. 项目概览

这是一个基于 Spring Boot 的模块化 AI 聊天后端。服务以 `ai-platform-boot` 为单一启动模块，对外提供 REST API 与 SSE 流式接口；MySQL 保存业务数据，Redis 为 Sa-Token 提供会话存储。

| 项目 | 当前实现 |
| --- | --- |
| Java | 17 |
| Web 框架 | Spring Boot 3.5.16、Spring MVC、Reactor `Flux` |
| 数据访问 | MyBatis-Plus、MySQL、Flyway |
| 认证与授权 | Sa-Token、Redis、角色权限模型 |
| AI 调用 | LangChain4j，支持 OpenAI、DeepSeek、Qwen 三类供应商配置 |
| 服务端口 | `8080`，可由 `SERVER_PORT` 覆盖 |

启动类为 `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`，它扫描 `com.example.aichat` 下的 Spring Bean，并注册系统、AI、聊天和后台统计模块的 Mapper。

## 2. 模块划分

```text
ai-platform-boot
 ├─ ai-platform-admin   后台 AI 配置与统计接口
 ├─ ai-platform-chat    会话、消息、流式生成
 ├─ ai-platform-system  用户、登录、角色、权限
 ├─ ai-platform-ai      模型配置、供应商适配、AI 调用与审计
 └─ ai-platform-common  统一响应、异常、当前用户与基础配置
```

| 模块 | 已实现职责 | 主要入口 |
| --- | --- | --- |
| `common` | 统一 API 响应、分页结果、业务异常、全局异常处理、Redis 序列化、当前用户抽象 | `ApiResponse`、`GlobalExceptionHandler`、`CurrentUserProvider` |
| `system` | 注册登录、会话控制、用户、角色与权限管理 | `AuthController`、`UserController`、`RoleController`、`PermissionController` |
| `ai` | AI 供应商/模型配置、凭证加密、模型路由、上下文裁剪、流式调用、调用日志与用量统计 | `DefaultAiChatService`、`AiModelRouter` |
| `chat` | 聊天会话、消息历史、SSE 生成与重新生成、生成任务状态管理 | `ChatController`、`ChatStreamOrchestrator` |
| `admin` | 供应商与模型后台管理、看板及用户/聊天/Token 统计 | `AiProviderController`、`AiModelController`、`AdminStatisticsController` |
| `boot` | 聚合各模块并作为可执行 Spring Boot 应用启动 | `AiPlatformApplication` |

## 3. 已实现业务能力

### 3.1 认证、用户、角色和权限

系统实现了基于 Sa-Token 的登录态认证和基于 RBAC 的接口授权。

- 用户可注册、登录、退出当前会话、退出全部会话、查询当前用户和登录状态、修改自己的密码。
- 密码通过 `BCryptPasswordEncoder` 处理。
- `/api/**` 默认要求登录；`/api/auth/register`、`/api/auth/login`、`/api/auth/status` 为匿名入口。
- `sys_user`、`sys_role`、`sys_permission` 及两张关联表组成用户—角色—权限数据模型。
- 后台用户、角色和权限接口使用 `@SaCheckPermission` 校验具体权限码。
- 聊天相关接口没有单独的权限码校验，但会在服务层验证会话或消息归属当前用户。
- 新注册用户默认未分配角色；禁用用户或修改密码会注销该用户的现有登录态。

| 功能 | 接口 |
| --- | --- |
| 注册 | `POST /api/auth/register` |
| 登录 | `POST /api/auth/login` |
| 退出当前会话 / 全部会话 | `POST /api/auth/logout` / `POST /api/auth/logout-all` |
| 当前用户 / 登录状态 | `GET /api/auth/me` / `GET /api/auth/status` |
| 修改当前用户密码 | `POST /api/auth/password` |
| 用户列表、详情、更新、启停、分配角色 | `GET /api/system/users`、`GET/PUT /api/system/users/{id}`、`PATCH /api/system/users/{id}/status`、`PUT /api/system/users/{id}/roles` |
| 角色增删改查与分配权限 | `GET/POST /api/system/roles`、`GET/PUT/DELETE /api/system/roles/{id}`、`PUT /api/system/roles/{id}/permissions` |
| 权限增删改查 | `GET/POST /api/system/permissions`、`GET/PUT/DELETE /api/system/permissions/{id}` |

### 3.2 AI 供应商与模型管理

后台可维护供应商和模型配置，供聊天服务在请求时选择可用模型。

- 供应商配置包含供应商代码、名称、类型、`baseUrl`、连接/读取超时、启用状态和排序。
- API Key 入库前使用 AES-256-GCM 加密，密文带 `v1:` 版本前缀；模型调用前解密。
- 模型配置包含所属供应商、模型代码和名称、上下文窗口、最大输出 Token、默认 `temperature` / `topP`、流式支持标记、默认模型标记与启用状态。
- 已注册三种供应商类型：`OPENAI`、`DEEPSEEK`、`QWEN`。三者均通过 OpenAI 兼容的 LangChain4j 流式模型适配器发起调用。
- 路由优先级为：请求指定模型 → 会话默认模型 → 系统默认且已启用的模型；路由后还会校验其供应商处于启用状态。
- 更新供应商时，未提交 API Key 会保留原有密文；将模型设为系统默认模型时，会清除其他模型的默认标记。

| 功能 | 接口 | 所需权限 |
| --- | --- | --- |
| 供应商列表、详情、新增、修改、删除 | `GET/POST /api/admin/ai/providers`、`GET/PUT/DELETE /api/admin/ai/providers/{id}` | `ai:provider:*` |
| 模型列表、详情、新增、修改、删除 | `GET/POST /api/admin/ai/models`、`GET/PUT/DELETE /api/admin/ai/models/{id}` | `ai:model:*` |

### 3.3 聊天会话和消息历史

每个会话属于一个用户，可设置标题、默认模型和置顶状态；删除采用逻辑删除。会话内消息用单调递增的 `sequence` 排序。

| 功能 | 接口 |
| --- | --- |
| 创建会话 | `POST /api/chat/sessions` |
| 分页查询自己的会话 | `GET /api/chat/sessions?page=&size=` |
| 查询会话详情 | `GET /api/chat/sessions/{id}` |
| 修改会话标题或默认模型 | `PUT /api/chat/sessions/{id}` |
| 设置或取消置顶 | `PUT /api/chat/sessions/{id}/pin?pinned=` |
| 删除会话 | `DELETE /api/chat/sessions/{id}` |
| 分页查询会话消息，可按消息状态过滤 | `GET /api/chat/sessions/{sessionId}/messages?page=&size=&status=` |

消息角色为 `USER` 或 `ASSISTANT`，消息状态包括 `PENDING`、`STREAMING`、`COMPLETED`、`FAILED`、`INTERRUPTED`。助手消息会记录实际模型、关联生成任务和请求 ID；异常结束时还会记录错误码与错误信息。

### 3.4 SSE 流式对话和重新生成

聊天生成通过 `text/event-stream` 返回 SSE 事件，事件名称是枚举名的小写形式：`start`、`delta`、`complete`、`failed`。

| 功能 | 接口 | 说明 |
| --- | --- | --- |
| 发起流式对话 | `POST /api/chat/sessions/{sessionId}/stream` | 创建用户消息、助手消息和生成任务后发起模型调用 |
| 重新生成助手回复 | `POST /api/chat/sessions/{sessionId}/regenerate` | 针对该会话中的既有助手消息，定位其前一条用户消息后再次生成 |

一次流式对话的已实现处理顺序如下：

1. 读取当前用户并验证会话所有权，解析本次要使用的模型。
2. 以会话 ID 获取进程内生成锁；同一会话已有生成任务时，直接返回 `failed` SSE 事件。
3. 创建或复用用户消息，创建状态为 `STREAMING` 的助手消息及状态为 `RUNNING` 的 `chat_generation_task` 记录。
4. 读取该会话历史中的全部用户消息和已完成的助手消息，并转换为 AI 消息上下文；重新生成时以源用户消息为历史边界。上下文按模型窗口扣除最大输出 Token 后裁剪，优先保留 system 消息和较新的消息。
5. 调用供应商流式接口，并将模型输出转换为 `delta` 事件。
6. 正常结束时保存完整助手回复，将消息/任务状态更新为 `COMPLETED` / `SUCCESS`，并返回 Token 用量；异常或客户端取消时，保存已累积的输出并更新为失败或中断状态。

生成任务状态为 `RUNNING`、`SUCCESS`、`FAILED`、`INTERRUPTED`。运行中的任务以请求 ID 缓存在 `ChatGenerationTaskManager`，在流结束后清除。

调用时，登录响应 `data` 中的 `tokenName` 与 `tokenValue` 应以同名请求头带入后续请求。创建会话后，可使用以下最小请求体发起对话；`modelId`、`requestId`、`temperature`、`topP`、`maxOutputTokens` 均为可选覆盖参数。

```http
POST /api/chat/sessions/{sessionId}/stream
{{tokenName}}: {{tokenValue}}
Content-Type: application/json

{"content":"你好"}
```

重新生成请求必须携带当前会话内助手消息的 `messageId`，其他模型与生成参数同样可选。SSE 的 `id` 为请求 ID；`data` 为 `ChatStreamEventVO`，字段包括 `type`、`taskId`、`requestId`、`delta`、`assistantMessageId`、`modelId`、`modelCode`、`usage`、`errorCode` 和 `message`。例如增量事件：

```text
event: delta
id: 3cf5c4e7-4b9b-4e9e-b8dc-xxxxxxxxxxxx
data: {"type":"DELTA","taskId":101,"requestId":"3cf5c4e7-4b9b-4e9e-b8dc-xxxxxxxxxxxx","delta":"你好","assistantMessageId":202,"modelId":3,"modelCode":"example-model","usage":null,"errorCode":null,"message":null}
```

请求参数、会话归属和模型校验发生在流创建前，失败时按常规 HTTP 错误响应返回；流创建后的模型调用或编排异常以 `failed` SSE 事件表示。

### 3.5 AI 调用审计与 Token 用量

每一次 AI 流式调用均会写入 `ai_call_log`：请求 ID、用户/会话/消息、供应商和模型、开始/结束时间、耗时、输入/输出/总 Token、调用状态、完成原因及错误信息。

- 调用成功时记录估算 Token 用量；失败和取消也会更新调用状态。
- 用量估算由 `TokenUsageCalculator` 完成；聊天流结束时，该用量会随 `complete` SSE 事件返回。
- `AiCallLogService` 提供总用量和按模型汇总能力，后台统计模块直接基于调用日志和聊天/用户表聚合数据。

### 3.6 后台看板与统计

后台统计接口均要求相应的 `admin:*:read` 权限，并要求 `from`、`to` 两个 ISO 日期参数，例如 `?from=2026-01-01&to=2026-02-01`。`to` 必须晚于 `from`，时间跨度不超过 365 天，且查询区间为 `[from, to)`。总用户和总会话统计为全部未逻辑删除记录；新增、活跃、消息、生成任务和 AI 调用指标按指定时间区间聚合。

| 功能 | 接口 | 已聚合指标 |
| --- | --- | --- |
| 看板概览 | `GET /api/admin/dashboard/overview` | 用户、会话、消息、AI 调用成功/失败数及 Token |
| 看板趋势 | `GET /api/admin/dashboard/trend` | 按日的新用户、活跃用户、会话、消息、AI 调用与 Token |
| 用户统计 | `GET /api/admin/statistics/users` | 总用户、启停状态、新增用户、活跃用户及日趋势 |
| 聊天统计 | `GET /api/admin/statistics/chat` | 会话、消息、生成任务、成功率及日趋势 |
| Token 统计 | `GET /api/admin/statistics/tokens?groupBy=&page=&size=` | 调用与 Token 汇总，可按日、模型、供应商或用户分组 |

## 4. 数据存储

数据库结构由 Flyway 自动迁移，迁移脚本位于各模块的 `src/main/resources/db/migration`。

| 业务域 | 数据表 | 用途 |
| --- | --- | --- |
| 系统 | `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` | 用户、角色、权限及关联关系 |
| 系统日志 | `sys_login_log`、`sys_operation_log` | 登录与操作日志表结构 |
| AI 配置 | `ai_provider`、`ai_model` | 供应商凭证与模型参数 |
| AI 审计 | `ai_call_log` | 每次模型调用的状态、耗时和 Token 用量 |
| 聊天 | `chat_session`、`chat_message`、`chat_generation_task` | 会话、消息和生成任务 |

表之间通过 ID 字段形成业务关联，例如 `chat_session.user_id`、`chat_message.session_id`、`chat_message.generation_task_id`、`ai_model.provider_id`。迁移脚本为查询场景建立了会话列表、消息顺序、任务状态、调用日志和统计时间范围等索引。

## 5. 通用接口行为

除 SSE 接口外，控制器统一返回以下结构：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {}
}
```

- 会话和消息分页接口返回 `PageResult`。Token 统计在 `TokenStatisticsDTO.groups` 中返回当前页的分组数据，不返回分组总数或总页数；其 `page`、`size` 参数同样受范围校验。
- 参数校验失败、业务异常和未预期异常由全局异常处理器转换为统一响应和相应 HTTP 状态码。
- 未登录访问受保护接口时，Sa-Token 过滤器返回统一的未认证响应。

## 6. 配置与基础设施

应用配置位于 `ai-platform-boot/src/main/resources/application.yml`，当前已接入的基础设施如下。

| 配置域 | 环境变量 | 当前默认值或用途 |
| --- | --- | --- |
| HTTP | `SERVER_PORT` | 服务端口，默认 `8080` |
| MySQL | `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD` | 业务数据源，默认库名为 `ai_chat` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DATABASE` | Sa-Token 与 RedisTemplate 连接 |
| AI 凭证 | `AI_CREDENTIAL_ENCRYPTION_KEY` | 供应商 API Key 的 AES-256-GCM 加解密密钥 |
| 线程池 | `ai.executor-*` | AI 执行器核心线程、最大线程与队列容量 |
| Sa-Token | `SA_TOKEN_NAME`、`SA_TOKEN_TIMEOUT`、`SA_TOKEN_SIGNING_KEY` | Token 名称和会话有效期；当前使用 UUID Token，`SA_TOKEN_SIGNING_KEY` 映射到 JWT 密钥配置但未参与当前 UUID 登录流程 |

Flyway 在默认配置下开启，迁移位置为 `classpath:db/migration`；`dev` Profile 中关闭 Flyway。Actuator 暴露 `health` 与 `info` 端点。

## 7. 新成员定位索引

| 想了解或修改的功能 | 优先阅读位置 |
| --- | --- |
| 应用装配和运行配置 | `ai-platform-boot` 的启动类与 `application.yml` |
| 登录、当前用户和权限判断 | `ai-platform-system` 的 `AuthServiceImpl`、`SaTokenConfig`、`StpInterfaceImpl` |
| 供应商/模型后台配置 | `ai-platform-admin` 的 `AiProviderController`、`AiModelController`；`ai-platform-ai` 的对应 Service |
| AI 模型选择与供应商调用 | `AiModelRouter`、`AiModelFactory`、`DefaultAiChatService`、`provider/adapter` |
| 流式聊天主链路 | `ChatServiceImpl`、`ChatStreamOrchestrator`、`ChatGenerationServiceImpl` |
| 会话、消息持久化和历史上下文 | `ChatSessionServiceImpl`、`ChatMessageServiceImpl`、对应 Mapper XML |
| 统计指标口径 | `AdminStatisticsServiceImpl`、`AdminStatisticsMapper.xml` |
| 数据表字段与索引 | 各模块 `src/main/resources/db/migration/*.sql` |
