# AI Tool Calling 第一阶段设计

## 目标

在现有聊天流中支持模型自动调用请求明确指定的 HTTP Tool 或 MCP Tool。模型提出工具调用后，后端执行工具，将结果放回模型上下文，继续请求模型，直到模型返回最终文本。第一阶段只支持 OpenAI-compatible provider（OpenAI、DeepSeek、Qwen），Ollama 保持现有普通聊天行为。

## 范围

包含：

- 聊天请求显式传入 `toolCodes`；
- 只向模型暴露请求指定且处于可执行状态的工具；
- 复用现有 HTTP/MCP 执行器、凭证处理、SSRF 防护、超时和调用日志；
- 支持单轮多个工具调用；
- 支持工具结果回填并继续模型请求；
- 支持 HTTP 配置中的字符串模板参数和 JSON 类型参数；
- 工具调用轮数限制和错误结果回填；
- 保持无 `toolCodes` 请求的现有行为不变。

不包含：

- 模型管理中的工具能力字段；
- Ollama 工具调用；
- 人工确认工具调用；
- 工具调用过程的前端 SSE 展示；
- 工具调用消息持久化；
- 新增 HTTP Tool 参数 Schema 数据库字段；
- 工具自动选择或会话级工具绑定。

## 模块边界

`ai-platform-ai` 定义供应商无关的工具调用 SPI，不依赖 `ai-platform-tool` 的实体或执行器。`ai-platform-tool` 已依赖 `ai-platform-ai`，由工具模块实现该 SPI，Spring 启动时由 AI 服务自动发现实现，不形成 Maven 循环依赖。

```text
ai-platform-chat
  -> ai-platform-ai.AiChatService
  -> ai-platform-ai.ModelProvider
  -> ai-platform-ai.AiToolProvider SPI
                                  ^
                                  |
                         ai-platform-tool bridge
                                  |
                    ToolExecutionService
                       /                 \
                HTTP executor       MCP executor
```

工具调用使用当前登录用户。AI 内部调用复用工具服务的执行授权语义，并确保 `tool:execute` 权限不会因为绕过 Controller 而失效。工具服务仍负责工具状态、MCP Server 状态、凭证、网络安全和执行审计。

## 请求数据流

`ChatRequestDTO` 和 `ChatRegenerateDTO` 增加最多 20 个 `toolCodes`，每个 code 最多 100 个字符。服务层去除重复值并拒绝空值。字段按以下链路透传：

```text
ChatRequestDTO
  -> ChatGenerationCommand
  -> AiChatGateway
  -> AiChatRequest
  -> DefaultAiChatService
```

没有传 `toolCodes` 时不创建工具上下文，模型请求和现有逻辑保持一致。重新生成请求也必须显式传入工具列表，不从历史消息推断工具权限。

## AI 工具 SPI

AI 模块新增以下供应商无关对象：

- 工具描述：tool code、展示名称、描述和 JSON Schema；
- 工具执行结果：成功标记、结果数据或安全错误信息；
- 工具提供者：按用户和 tool codes 查询工具，并按 tool code 执行工具；
- 工具调用上下文：当前用户、请求 ID、已解析的工具描述和执行入口。

`DefaultAiChatService` 在有 `toolCodes` 时先通过 SPI 完成工具预检，再把工具上下文传入模型 Provider。没有工具 Provider 实现时返回明确的 AI 工具不可用错误，而不是静默忽略请求。

## 工具目录与执行

`ai-platform-tool` 新增 SPI bridge：

1. 按请求中的 `toolCodes` 查询工具定义；
2. 验证工具存在、未删除、已启用且 `availability` 为 `AVAILABLE`；
3. MCP 工具使用已保存的 `inputSchema`；
4. HTTP 工具根据 `httpConfig` 动态生成参数 Schema；
5. 将 tool code 作为模型工具名，展示名称和描述作为模型描述；
6. 执行时构造现有 `ToolExecuteRequest`，调用 `ToolExecutionService`；
7. 将成功数据序列化为模型可读 JSON，将失败转换成脱敏的错误 tool result。

预检失败时，在第一次模型请求前返回错误。工具执行失败不直接中止整个模型循环，而是把错误作为 tool result 返回给模型，让模型决定重试、改参数或向用户说明。

## HTTP 参数 Schema

不新增字段，直接使用现有 `httpConfig`。递归扫描以下位置：

- `queryParams`；
- 普通 `headers`；
- `bodyTemplate`。

认证 Header、凭据、URL 和 HTTP Method 不参与 Schema 生成。

### 字符串模板

占位符嵌入字符串时，参数按字符串处理：

```json
{
  "queryParams": {
    "name": "{{city}}",
    "keyword": "weather-{{city}}",
    "count": "1",
    "language": "zh"
  }
}
```

生成的模型参数只有 `city`，静态的 `count` 和 `language` 不暴露给模型。执行时沿用 `TemplateValueResolver` 的字符串替换规则。

### JSON 类型占位符

当 JSON 节点完整匹配占位符时，保留模型传入的 JSON 类型：

```json
{
  "bodyTemplate": {
    "city": "{{city}}",
    "count": "{{count}}",
    "includeForecast": "{{includeForecast}}",
    "filters": "{{filters}}"
  }
}
```

模型可以传入字符串、数字、布尔值、对象或数组。现有 `TemplateValueResolver` 已支持完整占位符返回原始 JSON 节点，因此 Body 仍以 JSON 类型发送，不转换为字符串。

嵌套路径（例如 `{{customer.id}}`）生成嵌套对象参数。相同占位符只生成一个参数，并合并所有使用位置。所有出现的参数都加入 `required`。没有占位符的 HTTP 工具使用空对象作为参数。

Schema 生成只描述模型参数，不改变现有 HTTP 模板解析和远程请求行为。Query/Header 最终按 HTTP 协议转换为字符串，Body 保留 JSON 类型。

## 模型调用循环

OpenAI-compatible Provider 使用 LangChain4j 的 `ChatRequest` 和 `toolSpecifications`，不再只调用简单的消息列表重载。

每轮流程如下：

1. 使用初始聊天消息、工具 Schema 和生成参数创建 `ChatRequest`；
2. 流式转发模型文本 delta；
3. 模型完成本轮后检查 `AiMessage.toolExecutionRequests()`；
4. 没有 tool call 时完成整个 Flux；
5. 有一个或多个 tool call 时，先把完整 assistant tool-call message 放入上下文；
6. 按调用顺序执行本轮全部工具；
7. 为每个调用追加对应的 `ToolExecutionResultMessage`；
8. 进入下一轮模型请求。

同一轮的多个工具结果必须全部加入同一轮后续请求，不能拆成多个独立模型请求。工具执行使用阻塞调用的合适 Reactor 调度器，避免阻塞模型流回调线程；结果顺序按模型返回顺序保持稳定。

默认最多执行 8 轮工具调用，由 `ai.max-tool-rounds` 配置，配置值必须为正整数。超过上限后结束请求并返回 `AI_TOOL_LOOP_LIMIT`。工具结果序列化后超过 `ai.max-tool-result-bytes`（默认 65,536 字节）时，不截断结果，而是生成工具错误结果。工具参数始终使用 JSON parser 解析，不对序列化字符串做匹配。

Qwen 和 DeepSeek 继续继承 OpenAI-compatible 实现。Ollama 传入非空工具上下文时返回不支持工具调用的错误；不传工具时保持原有路径。

工具调用和工具结果只保存在本次请求的内存上下文中，聊天最终只持久化模型最后返回的文本，不改变现有聊天消息表结构。

## 错误与安全

新增或复用以下错误语义：

- 工具列表包含不存在、禁用或过期工具：请求预检失败，复用 `TOOL_NOT_FOUND`、`TOOL_DISABLED`、`TOOL_STALE`；
- 工具 Schema 无法生成或参数 JSON 非法：工具调用失败并回填 `TOOL_CONFIG_INVALID` 或 `TOOL_ARGUMENT_INVALID`；
- 工具执行超时、远端错误或权限失败：复用现有工具错误码，传递脱敏信息；
- provider 不支持工具：新增 `AI_TOOL_UNSUPPORTED`（`BAD_REQUEST`）；
- 工具循环超过 8 轮：新增 `AI_TOOL_LOOP_LIMIT`（`BAD_GATEWAY`）；
- 没有可用 AI 工具 Provider：新增 `AI_TOOL_UNAVAILABLE`（`SERVICE_UNAVAILABLE`）。

凭据、完整请求参数和完整响应不写入聊天 SSE。工具审计继续由 `ToolExecutionService` 负责，保持现有脱敏和长度限制。AI bridge 调用工具服务的内部入口必须执行与 `tool:execute` 相同的权限检查，不能因为不经过 Controller 而绕过授权。工具结果超过模型可接受大小时返回错误，不静默截断。

## 流式接口行为

第一阶段不新增工具专用 SSE 事件。模型执行工具期间前端只会看到文本流暂停，最终继续接收模型文本 delta，现有 `START`、`DELTA`、`COMPLETE`、`FAILED` 事件结构不变。后续可独立增加工具调用状态事件，不影响本阶段内部协议。

## 测试策略

### AI 模块

- 工具上下文创建、工具 Schema 传入和无工具回归；
- OpenAI-compatible provider 第一轮返回 tool call、第二轮返回最终文本；
- 验证 assistant tool-call 和所有 tool-result 都被加入下一轮请求；
- 多工具调用保持返回顺序；
- 非法 JSON 参数、工具执行失败和最大轮数；
- Ollama 无工具回归及带工具明确失败。

### 工具模块

- HTTP Query 示例只生成 `city`，静态参数不进入 Schema；
- 字符串拼接占位符正确替换；
- Body 中完整占位符保留数字、布尔、对象和数组类型；
- 嵌套路径和重复占位符；
- MCP 已保存 Schema 正确转换；
- 禁用、过期、未知工具预检拒绝；
- SPI 执行复用 `ToolExecutionService` 并保留审计。

### 聊天模块

- `toolCodes` 从 HTTP 请求透传到 AI 请求；
- 重新生成请求透传工具列表；
- 不传工具时现有文本、图片、知识库和流式回归测试保持通过。

完成后运行相关模块测试以及根目录 Maven 测试，确认未改变无工具聊天和现有工具执行行为。
