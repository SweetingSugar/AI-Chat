# Agent 模块设计

## 1. 目标

新增独立的 `ai-platform-agent` 模块，让每个登录用户都能创建和管理属于自己的 Agent。Agent 固定一个聊天模型、系统提示词、用户自己的知识库和平台全局工具；用户在现有聊天接口中选择 Agent 后，系统自动检索绑定知识库，并由模型按需调用绑定工具。

本次实现遵循“管理能力完整、运行链路复用现有实现”的原则，不复制模型调用、知识库检索或工具执行循环。

## 2. 范围

包含：

- 新增 `ai-platform-agent` Maven 模块；
- Agent 的创建、查询、更新、删除和启停；
- Agent 与用户私有知识库的绑定；
- Agent 与管理员维护的全局工具的绑定；
- Agent 固定聊天模型和系统提示词；
- 现有聊天请求增加可选 `agentId`；
- Agent 运行时校验、知识库检索、工具调用和现有 SSE 输出；
- Agent 表、知识库绑定表、工具绑定表及 MySQL Flyway 迁移；
- 单元测试、控制器测试、聊天回归测试和接口文档。

不包含：

- 管理员专属 Agent；
- 用户私有 HTTP/MCP 工具的创建和管理；
- Agent 版本、发布、复制、共享或协作编辑；
- 独立的 Agent 会话表和执行监控面板；
- 人工确认工具调用；
- 新的 Agent 专用 SSE 协议；
- 将知识库改造成模型显式调用的独立工具。

## 3. 已确认的业务规则

### 3.1 归属与权限

- 所有登录用户都可以创建 Agent；
- Agent 只能由所属用户查看、修改、启停和删除；
- Agent 只能由所属用户在聊天中使用；
- Agent 管理接口不需要管理员权限，但需要登录；
- 用户不能通过请求字段覆盖 Agent 的模型、系统提示词、知识库或工具配置；
- 工具实际执行继续要求当前用户拥有 `tool:execute` 权限。

### 3.2 资源绑定

- 知识库必须属于当前用户，且未删除、处于可用状态；
- 工具是管理员维护的全局工具，用户只能绑定当前可用的工具；
- Agent 最多绑定 10 个知识库和 20 个工具；
- 创建和更新时校验资源，运行时再次校验资源；
- 资源失效时请求整体失败，不自动过滤、不自动解绑、不使用剩余资源；
- 更新绑定关系采用整体替换，全部校验成功后才写入。

### 3.3 模型与提示词

- Agent 固定一个已启用的聊天模型；
- Agent 固定一个系统提示词；
- 使用 Agent 的请求不能临时指定其他模型或提示词；
- 不带 `agentId` 的聊天请求保持现有行为不变。

## 4. 模块边界

新增模块及依赖关系：

```text
ai-platform-agent
  ├── ai-platform-ai
  ├── ai-platform-knowledge
  ├── ai-platform-tool
  └── ai-platform-common

ai-platform-chat -> ai-platform-agent
ai-platform-boot -> ai-platform-agent
```

`ai-platform-agent` 负责 Agent 配置和运行时解析，但不负责发送模型请求。它依赖：

- `AiModelService`：校验固定聊天模型；
- `KnowledgeBaseRepository`：校验当前用户拥有的知识库；
- `ToolQueryService`：校验全局可用工具；
- `CurrentUserProvider`：控制器和用户边界使用当前登录用户。

`ai-platform-chat` 只依赖 Agent 模块的运行时接口，不直接访问 Agent 实体、Mapper 或绑定表。

Agent 管理 Controller 放在 `ai-platform-agent` 模块，知识库和工具的管理 Controller 保持在原模块或现有管理模块中。

## 5. 数据模型

Agent 数据存储在现有 MySQL 数据源。知识库实际存储在 PostgreSQL，绑定表只保存 ID，不建立跨数据库外键。

### 5.1 `agent_definition`

字段：

- `id bigint`：主键；
- `user_id bigint`：所属用户；
- `name varchar(100)`：Agent 名称；
- `description varchar(1000)`：描述；
- `system_prompt text`：系统提示词；
- `model_id bigint`：聊天模型 ID；
- `status tinyint`：0 禁用，1 启用；
- `deleted tinyint`：0 未删除，1 已删除；
- `create_time datetime`；
- `update_time datetime`。

约束和索引：

- `(user_id, name, deleted)` 唯一，允许删除后重新使用名称；
- `(user_id, status, deleted, update_time)` 用于用户列表；
- 不在数据库层建立跨模块用户或模型外键。

### 5.2 `agent_knowledge_base`

字段：

- `id bigint`：主键；
- `agent_id bigint`；
- `knowledge_base_id bigint`；
- `create_time datetime`。

约束：

- `(agent_id, knowledge_base_id)` 唯一；
- 为 `agent_id` 和 `knowledge_base_id` 建索引；
- Agent 删除时在同一事务中删除绑定关系。

### 5.3 `agent_tool`

字段：

- `id bigint`：主键；
- `agent_id bigint`；
- `tool_code varchar(100)`；
- `create_time datetime`。

约束：

- `(agent_id, tool_code)` 唯一；
- 为 `agent_id` 和 `tool_code` 建索引；
- 不复制工具 URL、认证信息或凭据。

迁移文件使用未占用的 `V15__agent_schema.sql`，放在 `ai-platform-agent/src/main/resources/db/migration`。

## 6. 管理 API

所有接口要求登录，不添加管理员权限注解：

```text
GET    /api/agents
GET    /api/agents/{id}
POST   /api/agents
PUT    /api/agents/{id}
DELETE /api/agents/{id}
PATCH  /api/agents/{id}/status
```

### 6.1 创建和更新请求

```json
{
  "name": "客服助手",
  "description": "回答产品和售后问题",
  "systemPrompt": "你是一个专业、准确的客服助手。",
  "modelId": 2,
  "knowledgeBaseIds": [10, 12],
  "toolCodes": ["order_query", "shipping_query"]
}
```

约束：

- `name` 必填，最多 100 个字符；
- `description` 最多 1000 个字符；
- `systemPrompt` 必填，最多 20,000 个字符；
- `modelId` 必须是已启用的聊天模型；
- 知识库最多 10 个，工具最多 20 个；
- ID 和 code 去重；
- 空白 code、超限数量、无效资源导致整个请求失败。

响应包含 Agent 基本信息、模型 ID、绑定知识库 ID、绑定工具 code、状态和时间字段。不返回其他用户数据或工具敏感配置。

### 6.2 列表、详情和状态

列表只返回当前用户的未删除 Agent，默认按更新时间倒序。详情、更新、删除和状态修改均使用 `(currentUserId, agentId)` 作为查询条件，其他用户的 Agent 统一按 Agent 不存在处理。

禁用 Agent 保留配置但不能用于聊天；删除为逻辑删除，并清理两类绑定关系。

## 7. 聊天接入

### 7.1 请求字段

在 `ChatRequestDTO` 和 `ChatRegenerateDTO` 增加可选的 `agentId`。

带 `agentId` 时，`ChatService` 使用当前用户 ID 调用 Agent 运行时解析接口。Agent 解析成功后：

- 使用 Agent 的 `modelId`；
- 使用 Agent 的 `systemPrompt`；
- 使用 Agent 的 `knowledgeBaseIds`；
- 使用 Agent 的 `toolCodes`。

请求中已有的 `modelId`、`knowledgeBaseIds` 和 `toolCodes` 不参与本次 Agent 请求，不能覆盖 Agent 配置。没有 `agentId` 时，继续使用原有字段和逻辑。

重新生成请求也必须明确传入 `agentId`，不从历史消息或历史工具调用推断 Agent 配置。

### 7.2 运行时接口

Agent 模块对 Chat 暴露供应商无关的运行时配置：

```text
AgentRuntimeService.resolve(Long userId, Long agentId)

AgentRuntimeConfig:
- agentId
- modelId
- systemPrompt
- knowledgeBaseIds
- toolCodes
```

运行时解析执行以下检查：

1. Agent 属于当前用户；
2. Agent 未删除且已启用；
3. 固定模型仍已启用；
4. 所有绑定知识库仍属于当前用户且可用；
5. 所有绑定工具仍为当前可用工具。

任一检查失败，在模型调用前返回 Agent 业务错误。

### 7.3 现有聊天编排复用

`ChatGenerationCommand` 增加 Agent 系统提示词所需字段，`ChatStreamOrchestrator` 在构造 AI 消息上下文时注入：

```text
Agent systemPrompt
知识库检索上下文
历史聊天消息
当前用户消息
```

Agent 系统提示词作为系统级消息处理。知识库上下文继续由 `KnowledgeRetrievalService` 根据用户问题检索，并按现有不可信来源规则注入；本次不新增知识库模型工具。

工具继续沿用现有 `AiToolProvider` 和 OpenAI-compatible/Ollama 工具调用循环。Agent 只提供绑定的 tool code，不执行工具，也不改变工具审计、超时、SSRF 防护和最大轮数限制。

整个请求继续输出现有 `START`、`DELTA`、`COMPLETE`、`FAILED` SSE 事件，不新增 Agent 专用事件。

## 8. 错误处理

新增 Agent 错误语义：

- `AGENT_NOT_FOUND`：Agent 不存在、不属于当前用户或已删除；
- `AGENT_DISABLED`：Agent 已禁用；
- `AGENT_MODEL_INVALID`：固定模型不存在或已禁用；
- `AGENT_KNOWLEDGE_BASE_INVALID`：绑定知识库不存在、不属于当前用户或不可用；
- `AGENT_TOOL_INVALID`：绑定工具不存在、禁用、过期或关联 MCP 服务不可用。

创建和更新失败时不写入部分绑定关系。运行时资源校验失败时不调用模型、不产生新的 AI 调用记录、不执行工具。

当前用户没有 `tool:execute` 权限时，沿用现有工具权限错误，不通过 Agent 路径绕过权限。

## 9. 安全约束

- 所有 Agent 查询必须带当前用户 ID；
- 不接受客户端传入 owner/user ID；
- Agent system prompt 只能由所属用户配置，并作为系统消息注入；
- 用户请求不能覆盖 Agent 的工具列表；
- 工具响应不向 Agent 管理 API 暴露 URL、认证 Header、凭据或密文；
- 工具实际执行必须经过现有 `ToolExecutionService` 和 `tool:execute` 权限校验；
- 知识库内容继续按不可信外部内容处理，不能覆盖系统指令；
- Agent、绑定关系和请求参数不记录凭据或完整工具结果。

## 10. 测试策略

### 10.1 Agent 模块

- 当前用户只能看到自己的 Agent；
- 创建、详情、更新、删除、启停成功路径；
- 同一用户名称唯一，不同用户可使用相同名称；
- 模型不存在、禁用模型被拒绝；
- 非当前用户知识库、删除知识库、禁用知识库被拒绝；
- 不可用、过期或不存在工具被拒绝；
- 重复绑定去重，数量超限被拒绝；
- 更新校验失败时旧绑定关系保持不变；
- 删除清理绑定关系；
- 运行时对 Agent 和全部绑定资源再次校验；
- 控制器验证统一响应和用户边界。

### 10.2 Chat 模块

- 无 `agentId` 的现有请求全部回归通过；
- Agent 正确覆盖模型、系统提示词、知识库和工具配置；
- 请求中的模型、知识库和工具字段不能覆盖 Agent 配置；
- 其他用户的 Agent 不可使用；
- Agent system prompt、知识库上下文和历史消息顺序正确；
- 工具调用继续经过原有权限、执行和审计链路；
- Agent 失效资源在模型请求前失败；
- 带 Agent 的重新生成请求正确使用指定 Agent。

### 10.3 集成验证

```bash
mvn -pl ai-platform-agent -am test
mvn -pl ai-platform-chat -am test
mvn test
mvn -DskipTests compile
```

同时检查：

- `ai-platform-agent` 不反向依赖 `ai-platform-chat`；
- 无 Agent 的聊天行为没有变化；
- `.DS_Store` 等已有未跟踪文件不被加入提交；
- 不输出凭据、完整工具参数或完整工具响应。

## 11. 文档更新

更新：

- `docs/backend-development-guide.md`：新增 Agent 模块、表结构、用户归属和运行时链路；
- `docs/frontend-api-integration.md`：新增 Agent CRUD、状态接口和聊天请求 `agentId` 字段；
- 如新增配置项，在 `ai-platform-boot/src/main/resources/application.yml` 补充说明。本次默认复用现有模型、知识库和工具限制，不新增配置项。

## 12. 兼容性

- 不带 `agentId` 的请求保持原有模型、知识库、工具和 SSE 行为；
- 现有 Agent 以外的聊天、知识库、工具管理接口不改变；
- 不修改知识库所有权模型；
- 不修改工具全局管理模型；
- 不新增第二套模型调用或工具执行循环。
