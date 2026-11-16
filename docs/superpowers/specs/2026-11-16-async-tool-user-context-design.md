# 异步 AI 工具调用的用户上下文设计

## 背景

AI 工具调用在 `OpenAiToolCallingLoop` 中通过 `Schedulers.boundedElastic()` 异步执行。Servlet 请求线程中的 Sa-Token 上下文是线程本地状态，无法自动传递到工具执行线程，因此 `StpUtil.switchTo()` 和 `CurrentUserProvider.requireCurrentUser()` 会抛出 `SaTokenContextException`。

## 目标

- 允许 HTTP/MCP 工具在异步线程中稳定执行。
- 保留 `tool:execute` 权限校验。
- 使用请求已经确定的 `userId` 完成工具执行和调用审计。
- 不在线程池任务中传播 Servlet 请求/响应对象。
- 保持现有 HTTP/MCP 执行器和工具调用循环行为不变。

## 方案

工具预检和执行都使用显式用户身份，而不是依赖异步线程中的 Sa-Token 请求上下文。

1. `ToolAiToolProvider.prepare` 已接收请求用户 ID。权限检查改为基于该用户 ID 的显式权限判断。
2. `ToolExecutionService.executeAsUser` 保留现有接口语义，但内部不再调用 `StpUtil.switchTo`。
3. 执行内部方法接收 `userId`，直接使用该 ID 进行 `ToolInvocationLog` 审计，不调用 `CurrentUserProvider.requireCurrentUser()`。
4. 用户无 `tool:execute` 权限时返回 `FORBIDDEN`，不能静默跳过授权检查。
5. `OpenAiToolCallingLoop` 继续使用 bounded elastic 线程执行阻塞的 HTTP/MCP 调用。

## 数据流

```text
ChatService 获取当前用户
  -> AiChatRequest.userId
  -> ToolAiToolProvider.prepare(userId, ...)
  -> 显式用户权限检查
  -> ToolExecutionService.executeAsUser(userId, ...)
  -> 按 toolType 选择 HTTP/MCP 执行器
  -> 使用 userId 写入调用日志
```

## 错误处理

- 工具不存在、禁用、过期和配置错误保持现有错误码。
- 无工具执行权限返回 `FORBIDDEN`。
- 异步线程不再产生 `SaTokenContextException`。
- 工具执行失败仍通过 `AiToolExecutionResult` 返回给模型。

## 测试

- 增加无 Sa-Token 请求上下文时调用 `executeAsUser` 的测试，验证显式用户 ID 可以完成执行和审计。
- 增加无 `tool:execute` 权限时的拒绝测试。
- 保留现有 HTTP/MCP 执行器和工具服务测试。
- 运行 `ai-platform-tool` 相关测试及根项目测试。
