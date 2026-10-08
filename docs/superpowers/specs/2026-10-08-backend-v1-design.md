# AI Chat 后端 V1 设计

## 1. 目标与范围

将现有单模块 Spring Boot 骨架改造成 Java 17、Spring Boot 3.x 的 Maven
多模块模块化单体。V1 交付一个可运行的闭环：账号登录与 RBAC、用户自己的
聊天会话与消息、通过统一 AI 服务进行流式对话，以及必要的后台统计和操作日志。

技术边界固定为 Spring Boot、LangChain4j、Sa-Token、MyBatis-Plus、MySQL 与
Redis；部署形态是单体，明确不引入 Spring Cloud、消息队列或微服务间通信。

V1 不包括注册/找回密码、文件上传、联网搜索、多租户、支付、异步任务队列和
模型供应商管理 UI。模型凭据仅从部署环境的配置读取，绝不通过 API 返回。

## 2. Maven 模块与依赖规则

根工程 `ai-platform-parent` 只负责版本管理、插件和六个子模块聚合：

| 模块 | 职责 | 允许依赖 |
| --- | --- | --- |
| `ai-platform-common` | 统一响应、异常、分页、审计值对象、当前用户门面 | 第三方基础库 |
| `ai-platform-system` | 用户、角色、权限、登录、Sa-Token 集成 | `common` |
| `ai-platform-ai` | 模型配置、提示词/上下文、LangChain4j 适配、流式 AI 服务 | `common` |
| `ai-platform-chat` | 会话、消息、归属校验、SSE 编排 | `common`、`ai` |
| `ai-platform-admin` | 管理员统计、操作日志查询 | `common`、`system`、`chat` |
| `ai-platform-boot` | 唯一可执行 Jar、配置和组件装配 | 以上五个领域模块 |

依赖只能由表格从右向左流动，禁止 `common` 依赖业务模块，禁止 `ai` 依赖
`chat`，禁止任意循环依赖。`system` 不会被其他业务模块以 Maven 依赖方式直接
调用：登录用户信息由 `common` 的 `CurrentUser` 接口提供，具体实现由 `boot`
装配 Sa-Token 适配器。这保留了表中的单向关系，也便于测试时替换。

包名采用 `com.example.aichat.<domain>.<layer>`，例如
`chat.conversation.api`、`chat.conversation.application`、
`chat.conversation.infrastructure.persistence`。每个领域内部按业务聚合分包，
不建立跨领域的全局 controller/service/mapper 包。

## 3. 横切约定

- REST 返回 `ApiResponse<T>`；业务错误使用带稳定错误码的 `BusinessException`，
  由 `@RestControllerAdvice` 统一转换。
- MyBatis-Plus 负责实体、Mapper 与分页；Mapper 只在所属聚合的基础设施层使用，
  controller 永不直接访问 Mapper。
- Sa-Token 以用户 ID 为 loginId。`/api/auth/login` 匿名，其余 `/api/**` 默认
  登录后可访问；带 `@SaCheckPermission` 的接口同时执行 RBAC 权限检查。
- Redis 由 Sa-Token 存储登录态；另以短 TTL 缓存权限集合。缓存失效采用角色或
  权限变更后的显式删除，不缓存聊天消息。
- 使用 UTC `datetime(3)` 存储时间，在 API 中以 ISO-8601 输出。ID 使用
  MyBatis-Plus 雪花 ID，逻辑删除仅用于用户、会话和后台可恢复记录。
- 密码采用 BCrypt 哈希；日志及异常中不得写入密码、token 或模型密钥。

## 4. 领域数据模型

数据库迁移使用版本化 SQL（Flyway）在 `boot` 启动时执行。初始迁移包含：

| 表 | 关键字段与约束 | 用途 |
| --- | --- | --- |
| `sys_user` | `id`、唯一 `username`、`password_hash`、`status`、审计字段 | 本地账号 |
| `sys_role` | `id`、唯一 `code`、`name` | RBAC 角色 |
| `sys_permission` | `id`、唯一 `code`、`name` | RBAC 权限 |
| `sys_user_role` | 唯一 `(user_id, role_id)` | 用户角色关联 |
| `sys_role_permission` | 唯一 `(role_id, permission_id)` | 角色权限关联 |
| `chat_conversation` | `id`、`user_id`、`title`、`last_message_at`、逻辑删除 | 用户会话；索引 `(user_id, last_message_at)` |
| `chat_message` | `id`、`conversation_id`、`user_id`、`role`、`content`、`model_name`、`status`、`sequence_no` | 消息；唯一 `(conversation_id, sequence_no)` |
| `sys_operation_log` | `id`、`operator_id`、`action`、`target_type`、`target_id`、`result`、`created_at` | 后台审计日志 |

任何以会话 ID 操作的应用服务均先查询 `user_id = CurrentUser.id` 且未删除的会话；
查询不到时统一返回 `CHAT_CONVERSATION_NOT_FOUND`，不暴露其他用户数据存在性。
消息列表还须以该归属会话为条件，不能凭消息 ID 单独读取或写入。

## 5. AI 与流式对话

`ai-platform-ai` 对外仅暴露：

```java
public interface AiChatService {
    Flux<AiChatDelta> stream(AiChatRequest request);
}
```

其 LangChain4j 实现从受控配置建立模型客户端，接收已归一化的历史上下文，产生
文本增量、完成事件或失败事件。它不知道 HTTP、Sa-Token、数据库或会话所有权。
开发与集成测试使用可替换的 `FakeAiChatService`，不请求真实模型供应商。

`chat` 的发送流程为：验证登录与会话归属 → 在事务中保存用户消息并预留 assistant
消息（`GENERATING`）→ 调用 `AiChatService.stream` → 将增量按顺序推送 SSE 并累积
内容 → 正常结束后更新 assistant 消息为 `COMPLETED`、更新时间和会话摘要；异常或
客户端断开则标为 `FAILED`/`CANCELLED` 并持久化已生成内容。每次流只允许一个
会话写入路径，避免 sequence 冲突。

SSE 端点为 `POST /api/chat/conversations/{conversationId}/messages:stream`，
响应 `text/event-stream`。事件名称和数据为：

| 事件 | 数据 |
| --- | --- |
| `message` | `{messageId, delta}` |
| `done` | `{messageId, status:"COMPLETED"}` |
| `error` | `{code, message, messageId?}` |

客户端只能获得自己的会话流。controller 仅解析请求、设置 SSE 响应并调用聊天应用
服务；不得直接操作 Mapper 或调用 LangChain4j。

## 6. HTTP API

| 领域 | 接口 | 权限/归属 |
| --- | --- | --- |
| 认证 | `POST /api/auth/login`、`POST /api/auth/logout`、`GET /api/auth/me` | 登录接口匿名；其余登录用户本人 |
| 会话 | `POST/GET /api/chat/conversations`、`GET/PATCH/DELETE /api/chat/conversations/{id}` | 当前用户自己 |
| 消息 | `GET /api/chat/conversations/{id}/messages`、流式发送端点 | 当前用户自己的会话 |
| 管理 | 用户、角色、权限的 CRUD；`GET /api/admin/statistics/overview`；操作日志查询 | 对应 `system:*`、`admin:*` 权限 |

会话删除为逻辑删除；管理员不通过普通聊天接口越权读取用户内容。管理统计只返回
聚合计数（用户数、会话数、消息数、按日趋势），不返回聊天文本。

## 7. 测试与交付顺序

每一模块的 Maven 测试必须在开始下一模块前通过：

1. `common`：响应、异常、分页与当前用户门面单元测试。
2. `system`：密码验证、登录/登出、角色权限计算和受保护接口集成测试。
3. `ai`：上下文裁剪与流式事件适配单元测试，使用假模型。
4. `chat`：归属隔离、消息持久化、完成/失败流状态，以及 MockMvc SSE 集成测试。
5. `admin`：权限拒绝、统计聚合与日志查询集成测试。
6. `boot`：Flyway、MySQL/Redis 测试容器和完整 Spring 上下文冒烟测试。

单元测试不依赖网络。集成测试使用 Testcontainers 启动 MySQL 和 Redis；若本地未
安装 Docker，则 Maven profile 明确跳过容器集成测试并保留可单独执行的单元测试。
根验证命令为 `mvn verify`，并在最终阶段额外执行 `mvn -pl ai-platform-boot -am
spring-boot:run` 的启动验证（使用非生产配置）。

## 8. 配置与运维

`application.yml` 只保留无敏感值的结构和 `${ENV_VAR:default}` 引用。数据库、
Redis、Sa-Token 签名密钥和模型 API Key 由环境变量或部署时的外部配置提供；提交的
测试 profile 使用 Testcontainers 动态属性。生产 profile 开启结构化访问日志、
异常错误码和健康检查，不暴露 actuator 的敏感端点。

## 9. 验收条件

1. 根工程和每个模块独立编译、测试通过，且 Maven Enforcer 检查 Java 17。
2. 未登录访问受保护 API 返回认证错误；权限不足返回授权错误。
3. 用户 A 无法列出、读取、修改、删除或向用户 B 的会话发送消息。
4. 一条聊天请求可看到有序 SSE 增量，最终 assistant 消息在 MySQL 中处于准确状态。
5. AI provider 故障不会丢失用户消息，且不会遗留 `GENERATING` 状态。
6. 管理接口受 RBAC 保护，统计与操作日志可查询且不泄露消息正文。
