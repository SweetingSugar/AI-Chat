# Ollama 模型调用方式设计

## 目标

在 `ai-platform-ai` 模块中增加原生 Ollama 模型调用能力，使后台配置的 `OLLAMA` 供应商可以沿用现有聊天、流式 SSE、调用日志和模型路由链路完成对话。

本次只增加供应商适配能力，不调整聊天接口、模型路由优先级、流式事件格式和调用统计流程。

## 现有约束

- 模型调用通过 `ModelProvider` 接口统一暴露 `stream` 方法。
- `AiModelFactory` 根据 `AiProviderType` 选择适配器。
- OpenAI、DeepSeek、Qwen 当前使用 LangChain4j 流式模型，并通过 `LangChain4jStreamAdapter` 转换为内部事件。
- 供应商的 `baseUrl`、超时、模型代码、默认生成参数已包含在 `AiModelConfigSnapshot` 中。
- 供应商 API Key 当前以密文保存，但原生 Ollama 不需要 API Key。

## 方案

新增独立的 `OllamaModelProvider`，使用 LangChain4j 原生 Ollama 模块的 `OllamaStreamingChatModel`：

1. 在 `AiProviderType` 增加 `OLLAMA`。
2. 在 AI 模块引入 `langchain4j-ollama` 依赖。
3. `OllamaModelProvider` 实现 `ModelProvider`，仅支持 `OLLAMA`。
4. 适配器从快照读取：
   - `baseUrl` 作为 Ollama 服务地址；
   - `modelCode` 作为 Ollama 模型名；
   - `readTimeout` 作为请求超时；
   - 请求级 `temperature` / `topP` 优先于模型默认值。
5. 使用现有 `MessageConverter` 生成 LangChain4j 消息，并复用 `LangChain4jStreamAdapter` 输出 `AiStreamEvent`。
6. 适配器发生运行时异常时，沿用现有 `AI_PROVIDER_ERROR` 错误事件，不向客户端暴露供应商内部异常。

Ollama 的服务地址和模型名称由现有后台供应商、模型配置维护，不在代码中硬编码。典型配置为 `baseUrl=http://localhost:11434`，模型编码填写本地已拉取的 Ollama 模型名。

## 凭据和数据库变更

- 新增 Flyway 迁移，将 `ai_provider.api_key_ciphertext` 改为可空，以支持原生 Ollama 供应商。
- 创建供应商时：
  - `OLLAMA` 允许 API Key 为空，并保存空凭据；
  - 其他供应商仍必须提供 API Key。
- 更新供应商时：
  - 提供 API Key 时按现有流程加密并更新；
  - 未提供 API Key 时保留原凭据；
  - Ollama 不会解析或发送该字段。
- 其他供应商的加密、解密和管理端返回 `hasApiKey` 行为保持不变。

## 数据流

```text
聊天请求
  -> AiModelRouter
  -> AiModelFactory
  -> OllamaModelProvider
  -> OllamaStreamingChatModel
  -> LangChain4jStreamAdapter
  -> AiStreamEvent
  -> 现有 SSE / 日志 / Token 统计链路
```

`DefaultAiChatService`、`AiModelRouter`、`AiResponseAssembler` 和 `AiUsageRecorder` 不需要为 Ollama 增加分支。

## 错误处理

- Ollama 服务不可用、模型不存在、请求超时或流读取失败，统一转成现有的 `AI_PROVIDER_ERROR` 流事件。
- 供应商未启用、模型未启用和未配置默认模型仍由现有路由和服务层处理。
- 不记录 API Key 明文，也不把 Ollama 的内部异常消息返回给客户端。

## 测试策略

增加单元测试覆盖：

- `AiModelFactory` 能将 `OLLAMA` 快照路由到 Ollama 适配器。
- Ollama 适配器只支持 `OLLAMA`，并能使用快照中的服务地址和模型代码创建流式模型。
- 请求级 `temperature`、`topP` 覆盖模型默认值；未提供时使用快照默认值。
- Ollama 适配器不要求调用凭据解密器，空 API Key 可以完成模型构建。
- 供应商创建时 Ollama 可省略 API Key，其他供应商仍拒绝空 API Key。
- 供应商更新时省略 API Key 会保留已有凭据。

测试不依赖运行中的 Ollama 服务；真实服务连通性属于部署验收范围，不纳入 Maven 单元测试。

## 范围外事项

- 不增加 Ollama 非流式调用接口。
- 不实现 Ollama 模型自动发现、拉取、删除或健康检查。
- 不改变现有聊天请求和 SSE 协议。
- 不为 Ollama 额外设计独立配置表或独立凭据字段。
- 不重构现有 OpenAI 兼容供应商适配器。

## 完成标准

- 可通过后台创建无 API Key 的 `OLLAMA` 供应商和模型。
- 配置启用的 Ollama 模型后，聊天请求能进入原生 Ollama LangChain4j 适配器并产生现有 SSE 流事件。
- 现有 OpenAI、DeepSeek、Qwen 调用行为不变。
- AI 模块及受影响模块的 Maven 测试通过。
- 后端开发文档列出 `OLLAMA` 类型和配置示例。
