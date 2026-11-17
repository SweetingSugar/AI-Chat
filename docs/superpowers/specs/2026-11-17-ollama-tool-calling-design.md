# Ollama 原生 Tool Calling 设计

## 目标

为 Ollama 模型增加原生 Tool Calling 能力，使其可以使用聊天请求中明确指定的 AI Tool，同时保留现有的普通聊天、图片消息、流式 SSE、模型路由和工具执行安全边界。

本次只支持 Ollama 原生工具协议，不通过提示词模拟工具调用。具体模型是否支持工具调用由 Ollama 模型自身决定；不支持时返回明确的 provider 错误。

## 范围

包含：

- Ollama 请求携带 LangChain4j `ChatRequest.toolSpecifications()` 转换出的原生 `tools`；
- 解析 Ollama 返回的原生 `tool_calls`；
- 复用现有工具调用循环执行工具，并把工具结果加入下一轮模型上下文；
- 保持工具权限、工具执行审计、结果大小限制和最大循环轮数限制；
- 增加 Ollama 原生工具调用及无工具回归测试。

不包含：

- 不支持 Ollama 不具备原生 Tool Calling 能力的模型的提示词模拟；
- 不新增工具专用 SSE 事件；
- 不改变聊天消息数据库结构；
- 不改变 HTTP/MCP 工具执行器和权限模型。

## 现有基础

`ModelProvider` 已通过带 `AiToolContext` 的 `stream` 重载表达工具调用能力。OpenAI 兼容 provider 使用 `OpenAiToolCallingLoop`，该循环依赖通用的 LangChain4j `StreamingChatModel`，负责：

1. 创建包含工具 Schema 的 `ChatRequest`；
2. 流式转发模型文本；
3. 读取 `AiMessage.toolExecutionRequests()`；
4. 按顺序执行工具；
5. 将 assistant tool-call message 和所有 tool result 加入上下文；
6. 继续请求模型，直到得到最终文本或达到轮数限制。

LangChain4j 1.21.0 的 Ollama 适配器已经支持：

- 将 `ChatRequest.toolSpecifications()` 转成 Ollama `/api/chat` 的 `tools` 字段；
- 将 Ollama 的 `message.tool_calls` 转成 `AiMessage.toolExecutionRequests()`；
- 将 assistant 工具调用和 `ToolExecutionResultMessage` 转回 Ollama 后续请求需要的消息格式。

因此不新增 HTTP 客户端，也不复制工具调用循环。

## 架构与数据流

`OllamaModelProvider` 在构造 `OllamaStreamingChatModel` 后，根据 `AiToolContext` 分支：

- `toolContext == null`：继续使用 `LangChain4jStreamAdapter.adapt(model, messages)`，维持现有无工具路径；
- `toolContext != null`：使用 `OpenAiToolCallingLoop(model, toolContext, objectMapper, maxToolRounds, maxToolResultBytes)`，进入原生工具调用循环。

数据流如下：

```text
ChatRequest + AiToolContext
        |
        v
OllamaModelProvider
        |
        v
OllamaStreamingChatModel
        |
        +--> Ollama /api/chat: tools + messages
        |
        +<-- assistant text delta / tool_calls
        |
        v
OpenAiToolCallingLoop
        |
        +--> ToolAiToolProvider / AiToolContext executor
        |
        +<-- ToolExecutionResultMessage
        |
        +--> 下一轮 Ollama /api/chat
        |
        v
最终文本 delta -> 现有 SSE 流
```

工具上下文仍由现有 AI Tool Provider 创建，工具执行继续经过现有 `ToolExecutionService`，不绕过权限检查、凭证处理、URL 安全校验、超时和审计逻辑。

## 错误处理

- Ollama 模型不支持原生工具调用，或 Ollama 返回 provider 错误：沿用 `AI_PROVIDER_ERROR`，不尝试降级为提示词模拟；
- 工具参数非法、工具不存在、工具执行失败或工具结果超限：沿用 `OpenAiToolCallingLoop` 的错误 tool result，让模型决定重试或向用户说明；
- 工具循环超过 `ai.max-tool-rounds`：沿用 `AI_TOOL_LOOP_LIMIT`；
- 请求没有工具上下文：不改变现有错误和流式行为。

工具调用消息和工具结果仅存在于当前请求内存上下文，不通过 SSE 暴露，也不持久化为聊天消息。

## 实现变更

1. 修改 `OllamaModelProvider` 的带工具 `stream` 重载：移除直接返回 `AI_TOOL_UNSUPPORTED` 的分支，构造 Ollama 模型后复用 `OpenAiToolCallingLoop`。
2. 保留无工具重载及模型参数配置，包括 endpoint、model、timeout、temperature 和 top-p。
3. 删除仅因旧的不支持行为而不再需要的 import；不改变公共 provider 接口。
4. 在 `OllamaModelProviderTest` 中增加原生工具请求测试：验证请求包含工具定义、首次响应包含 `tool_calls`、第二轮携带 assistant tool call 和 tool result，并最终产生文本。
5. 增加不支持/错误响应的错误事件测试，并保留现有无工具和图片消息测试。

## 测试策略

### Provider 单元/集成风格测试

使用本地 `HttpServer` 模拟 Ollama `/api/chat` NDJSON 流：

- 第一轮返回 `tool_calls`，记录请求体；
- 第二轮返回最终 assistant 文本，记录请求体；
- 使用假的 `AiToolContext` 验证工具收到名称和 JSON 参数；
- 断言最终事件包含工具执行后的最终文本；
- 断言第二轮请求包含 assistant 的工具调用和对应的 tool result，而不是只发送用户消息；
- 断言工具 Schema 出现在第一轮 `tools` 字段；
- 断言 Ollama 普通聊天和图片消息现有测试继续通过；
- 模拟 Ollama 错误响应，断言返回稳定的 `AI_PROVIDER_ERROR` 事件。

### 验证命令

```bash
mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest test
mvn -pl ai-platform-ai -am test
mvn test
```

实现完成后还需检查最终 diff，确认没有新增 `ai-platform-tool` 反向依赖、没有把凭证或完整工具参数写入 SSE/持久化消息，也没有改变无工具聊天路径。
