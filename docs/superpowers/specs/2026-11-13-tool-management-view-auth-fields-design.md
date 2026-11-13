# ToolManagementView 认证字段设计

## 目标

管理端工具视图需要返回工具的认证类型和 API key 请求头名称，使调用方能够完整展示已保存的认证配置，同时不暴露凭据密文。

## 方案

在 `ToolManagementView` record 中新增：

- `ToolAuthType authType`
- `String authHeaderName`

字段放在 `remoteToolName` 之后、`httpConfig` 之前，与 `ToolDefinition` 中已有字段保持对应关系。

不抽取新的映射器，也不从 `httpConfig` 或请求对象推导认证信息。现有两个视图映射方法分别将实体中的认证字段直接传入视图：

- `ToolManagementServiceImpl.toView`：手工维护的 HTTP 工具透传 `ToolDefinition.authType` 和 `ToolDefinition.authHeaderName`。
- `ToolDiscoveryPersistenceServiceImpl.toView`：发现的 MCP 工具同样透传实体字段。新建 MCP 快照已有 `authType = NONE`，其 `authHeaderName` 保持 `null`。

## 数据流和安全边界

工具创建或更新时，认证类型和请求头名称已经由 `ToolManagementServiceImpl.copy` 校验并保存到 `ToolDefinition`。视图映射只读取这两个非敏感配置字段；凭据密文仍只用于 `hasCredential` 判断，不加入响应。

MCP 发现刷新只更新工具名称、描述、输入 schema 和可用性，不改变认证字段，因此刷新后的视图继续反映实体中的认证配置。

## 测试

补充或调整服务层测试：

1. 手工 HTTP 工具创建后，视图返回 `ToolAuthType.BEARER` 和 `null` 请求头名称。
2. API key 工具的视图返回保存的 `ToolAuthType.API_KEY` 和配置的请求头名称。
3. MCP 发现结果视图返回 `ToolAuthType.NONE` 和 `null`。
4. 保留现有凭据脱敏断言，确保新增字段不会导致凭据密文进入响应。

## 范围

本次只修改视图字段声明、构造调用和覆盖这些映射的测试；不改变认证校验、数据库结构、执行器行为或管理接口路径。由于该 record 被管理接口直接返回，字段会随现有接口响应自动序列化。
