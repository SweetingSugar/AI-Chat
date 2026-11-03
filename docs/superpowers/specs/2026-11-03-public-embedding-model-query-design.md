# 公共 Embedding 模型查询接口设计

## 目标

为所有已登录用户提供一个只读接口，查询当前可用的 `EMBEDDING` 类型模型，供知识库等用户侧功能选择模型。

接口不提供管理能力，不返回停用模型或所属 Provider 已停用的模型。

## 接口

```http
GET /api/ai/models/embedding
```

接口沿用项目统一的 `ApiResponse` 返回结构，并要求登录但不要求业务权限码。当前 `/api/**` 默认经过 Sa-Token 登录校验，因此不需要修改匿名白名单。

## 模块边界

### `ai-platform-ai`

AI 模块负责模型目录的查询能力：

- 在 `AiModelQueryService` 增加查询可用 Embedding 模型的方法。
- 在 `AiModelServiceImpl` 中按模型类型、模型状态和 Provider 状态过滤。
- 新增面向用户侧的 Embedding 模型 DTO，至少返回模型 ID、Provider 信息、模型编码、模型名称和向量维度。
- 新增 `AiModelQueryController` 暴露 HTTP 接口。

由于该模块目前只有 Spring 基础依赖，Controller 所需的 Web 依赖需要补充到 `ai-platform-ai/pom.xml`。

### 不修改的模块

- `ai-platform-admin` 继续只负责带权限的模型管理接口。
- `ai-platform-chat` 继续只提供聊天模型列表，不将 Embedding 模型混入聊天接口。
- `ai-platform-knowledge` 通过 `AiModelQueryService` 或 HTTP 客户端使用模型目录，不承担 AI 模型目录的归属。

## 查询规则

查询 `ai_model` 时使用以下条件：

1. `model_type = EMBEDDING`。
2. `status = 1`。
3. 关联 Provider 的 `status = 1`。
4. 按模型 `sort` 升序、模型 ID 升序返回。

模型的 `embeddingDimension` 必须返回给客户端，因为知识库创建和向量索引需要根据该值判断维度兼容性。

## 数据流

```text
HTTP GET /api/ai/models/embedding
    -> AiModelQueryController
    -> AiModelQueryService
    -> AiModelServiceImpl
    -> AiModelMapper + AiProviderService
    -> AiEmbeddingModelDTO 列表
    -> ApiResponse
```

Provider 查询失败或 Provider 已停用时，该模型不出现在公共列表中。已有的 `AiModelService.requireEnabledEmbedding` 继续负责具体模型使用时的强校验，本接口不替代该校验。

## 错误与安全

- 未登录请求由 Sa-Token 返回统一未认证响应。
- 不增加 `@SaCheckPermission`，所有已登录用户都能读取列表。
- 不返回 Provider 凭证、模型内部配置、温度、上下文窗口等不需要用于选择 Embedding 模型的字段。
- 列表为空时返回成功响应和空数组，不视为异常。

## 测试

覆盖以下行为：

1. 只返回启用的 Embedding 模型。
2. 排除 Chat 模型。
3. 排除停用模型。
4. 排除所属 Provider 已停用的模型。
5. 返回模型 ID、Provider 信息、模型编码、模型名称和 Embedding 维度。
6. Controller 返回统一的 `ApiResponse`，且不要求权限注解。
7. 保证现有聊天模型列表仍然只返回 Chat 模型。

## 范围

本次不新增匿名访问、不修改数据库结构、不修改模型管理接口、不改变 Embedding 模型调用和知识库索引流程。
