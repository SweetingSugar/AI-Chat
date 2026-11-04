# ChatModelController Embedding 模型查询接口设计

## 目标

在现有 `ChatModelController` 中新增用户侧只读接口，查询当前可用的 `EMBEDDING` 类型模型，供知识库等功能选择模型。

本次接口只返回可用模型，不提供模型管理能力，不改变现有聊天模型列表、模型管理接口或 Embedding 调用流程。

## 接口

```http
GET /api/chat/models/embedding
```

接口沿用项目统一的 `ApiResponse` 返回结构，并通过现有 `/api/**` Sa-Token 拦截器要求登录。不增加业务权限注解，所有已登录用户均可读取列表。

现有接口保持不变：

```http
GET /api/chat/models
```

该接口仍然只返回 `CHAT` 类型模型。

## 模块边界

### `ai-platform-ai`

AI 模块负责模型目录查询能力：

- 在 `AiModelQueryService` 增加 `listAvailableEmbeddingModels()` 方法。
- 在 `AiModelServiceImpl` 中实现 Embedding 模型查询和可用性过滤。
- 新增专用 `AiEmbeddingModelDTO`，返回用户选择模型所需的公开字段。

### `ai-platform-chat`

Chat 模块负责 HTTP 暴露：

- 在 `ChatModelController` 新增 `GET /embedding` 方法。
- 依赖 `AiModelQueryService`，不直接依赖模型管理服务。
- 不新增权限注解，不改变现有 `GET /api/chat/models` 方法。

## 返回结构

`AiEmbeddingModelDTO` 返回以下字段：

- `id`：模型 ID。
- `providerId`：Provider ID。
- `providerName`：Provider 名称。
- `modelCode`：模型编码。
- `modelName`：模型名称。
- `embeddingDimension`：向量维度。

不返回 Provider 凭证、内部配置、温度、上下文窗口等不用于选择 Embedding 模型的字段。

## 查询规则

查询 `ai_model` 时使用以下规则：

1. 只选择 `model_type = EMBEDDING`。
2. 只选择 `status = 1` 的模型。
3. 只保留所属 Provider `status = 1` 的模型。
4. 按模型 `sort` 升序、模型 ID 升序返回。
5. 模型的 `embeddingDimension` 原样返回；数据约束保证可用 Embedding 模型具有正数维度。

Provider 查询失败或 Provider 已停用时，该模型从公共列表中排除。具体模型使用时仍由现有 `requireEnabledEmbedding` 执行强校验，本接口不替代该校验。

## 数据流

```text
HTTP GET /api/chat/models/embedding
    -> ChatModelController
    -> AiModelQueryService
    -> AiModelServiceImpl
    -> AiModelMapper + AiProviderService
    -> AiEmbeddingModelDTO 列表
    -> ApiResponse
```

## 错误与安全

- 未登录请求由现有 Sa-Token 拦截器返回统一未认证响应。
- 不增加 `@SaCheckPermission`，所有已登录用户都能读取列表。
- 无可用模型时返回成功响应和空数组，不视为异常。
- Provider 查询异常按现有用户侧模型列表逻辑处理，将对应模型排除，不向调用方暴露 Provider 内部错误或敏感配置。

## 测试

服务层测试覆盖：

1. 只返回启用的 Embedding 模型。
2. 排除 Chat 模型。
3. 排除停用模型。
4. 排除所属 Provider 已停用或查询失败的模型。
5. 验证按 `sort`、模型 ID 排序。
6. 验证 DTO 返回模型 ID、Provider 信息、模型编码、模型名称和 Embedding 维度。
7. 保证现有聊天模型列表仍然只返回 Chat 模型。

Controller 测试覆盖：

1. `GET /api/chat/models/embedding` 返回 HTTP 200。
2. 响应使用统一 `ApiResponse` 结构并包含 Embedding DTO 字段。
3. Controller 不要求业务权限注解，并调用查询服务的新方法。

## 范围

本次不做以下修改：

- 不新增匿名访问或修改登录白名单。
- 不修改数据库结构和迁移脚本。
- 不修改 AI 模型管理接口。
- 不扩展或改变现有聊天模型 DTO。
- 不改变 Embedding 模型调用和知识库索引流程。
