# Tool URL 回环地址访问设计

## 背景

`ai-platform-tool` 的 `UrlSafetyValidator` 当前会拒绝 loopback 地址，因此 MCP Server 和 HTTP Tool 无法访问本机服务，例如 `localhost`、`127.0.0.1` 和 `::1`。现有 `tool.allow-private-network` 只控制私有网段，不会放行回环地址。

## 目标与范围

本次修改目标：

- 默认允许 URL 解析到 IPv4 loopback 地址，例如 `127.0.0.1`；
- 默认允许 URL 解析到 IPv6 loopback 地址，例如 `::1`；
- 允许通过 `localhost` 访问本机服务；
- MCP Server URL 和 HTTP Tool URL 使用相同规则；
- 保持其他 SSRF 防护规则不变。

不在本次范围内：

- 默认放开 `10.x.x.x`、`172.16.x.x`、`192.168.x.x` 等私有网段；
- 默认放开 `0.0.0.0`、`::` 等任意本地监听地址；
- 默认放开 link-local 地址；
- 新增环境开关或配置项；
- 修改 MCP 或 HTTP 执行协议。

## 方案

直接调整 `UrlSafetyValidator` 的地址分类：

- 从始终阻断集合中移除普通 loopback 地址；
- 从始终阻断集合中移除 IPv4-mapped loopback 地址；
- 继续阻断 any-local 地址（`0.0.0.0`、`::` 等）；
- 继续阻断 link-local 地址；
- 当 `allow-private-network=false` 时，继续阻断其他私有网段；
- 当 `allow-private-network=true` 时，继续沿用现有私有网段放行行为。

不对 `localhost` 做字符串特判。校验器继续解析主机名并检查解析出的所有 IP 地址，因此 `localhost` 会自然覆盖 IPv4 和 IPv6 回环解析结果，同时避免绕过 DNS 解析校验。

## 校验行为

| 地址 | 默认结果 | 说明 |
| --- | --- | --- |
| `127.0.0.1` | 允许 | IPv4 loopback |
| `localhost` | 允许 | 通常解析为 `127.0.0.1` 和/或 `::1` |
| `::1` | 允许 | IPv6 loopback |
| IPv4-mapped loopback | 允许 | 等价于 IPv4 loopback |
| `0.0.0.0`、`::` | 拒绝 | any-local 地址 |
| link-local 地址 | 拒绝 | 保持 SSRF 防护 |
| 私有网段 | 拒绝 | 仍由 `allow-private-network` 控制 |

## 实现位置

- `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/UrlSafetyValidator.java`
  - 调整 loopback 与 IPv4-mapped loopback 的阻断逻辑。
- `ai-platform-tool/src/test/java/com/example/aichat/tool/validation/UrlSafetyValidatorTest.java`
  - 更新 loopback 默认拒绝测试；
  - 增加 `localhost`、IPv6 loopback、IPv4-mapped loopback 的放行测试；
  - 保留私有网段、any-local、非 HTTP(S) 和 URL 用户信息的拒绝测试。

## 测试策略

- 使用默认 `allowPrivateNetwork=false` 验证 `127.0.0.1`、`localhost` 和 `::1` 可以通过校验；
- 验证 `0.0.0.0` 和 `::` 仍然被拒绝；
- 验证私有网段在未显式允许时仍被拒绝；
- 验证 `allowPrivateNetwork=true` 的既有私有网段行为不变；
- 运行 `UrlSafetyValidatorTest` 及 `ai-platform-tool` 模块测试。

## 安全影响

此次修改会使所有环境默认允许 MCP 和 HTTP Tool 访问本机回环服务。由于这是直接默认放行而不是新增配置开关，生产环境中的工具创建权限、工具 URL 配置来源和权限控制必须保持受控。除回环地址外，其他本地网络和私有网段仍遵守原有安全策略。
