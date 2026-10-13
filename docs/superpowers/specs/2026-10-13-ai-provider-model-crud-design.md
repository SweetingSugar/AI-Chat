# AI 供应商与模型管理设计

## 目标

为后台管理员提供 AI 供应商和模型配置的 REST CRUD 能力。供应商 API Key 可由
管理员录入或更新，但只以加密形式保存在数据库中，且绝不通过任何读取接口返回。

## 模块边界

- `ai-platform-ai` 负责供应商和模型的实体、Mapper、请求/响应对象、应用服务、
  校验规则与凭据加密/解密。
- `ai-platform-admin` 负责后台 REST Controller 与权限声明；其依赖 `ai-platform-ai`。
- `ai-platform-boot` 继续作为配置装配与 Flyway 迁移执行入口。

接口 Controller 位于 `com.example.aichat.admin.controller`。AI 模块不依赖 admin，
以维持现有 Maven 依赖方向。

## HTTP API 与权限

供应商接口位于 `/api/admin/ai/providers`，模型接口位于
`/api/admin/ai/models`。每类资源均提供以下接口：

| 方法 | 路径 | 权限 | 行为 |
| --- | --- | --- | --- |
| GET | 集合路径 | `ai:*:read` | 按排序值、ID 返回未删除记录 |
| GET | `/{id}` | `ai:*:read` | 查询单条记录 |
| POST | 集合路径 | `ai:*:create` | 创建记录 |
| PUT | `/{id}` | `ai:*:update` | 替换可编辑配置 |
| DELETE | `/{id}` | `ai:*:delete` | 逻辑删除记录 |

实际权限中的 `*` 分别是 `provider` 与 `model`。响应统一使用
`ApiResponse<T>`；所有业务冲突返回 `RESOURCE_CONFLICT`，不存在的资源返回
`RESOURCE_NOT_FOUND`。

供应商写入字段包含编码、名称、类型、Base URL、超时、状态、排序、备注和可选的
`apiKey`。供应商读取响应只公开 `hasApiKey` 和 `credentialVersion`，不公开
`apiKey`、`apiKeyCiphertext` 或解密值。更新请求中未提供或为空白的 `apiKey` 代表
保留既有密钥；创建时 `apiKey` 必填。

模型写入字段包含供应商 ID、编码、名称、类型、上下文窗口、最大输出 token、
temperature、top-p、是否支持流、是否默认、状态、排序和备注。读取响应可展示关联
供应商名称，但不携带其密钥信息。

## 持久化与领域规则

Flyway `V2` 创建 `ai_provider` 与 `ai_model` 表，两个表都使用现有的逻辑删除
字段及审计时间字段。约束包括：

- `ai_provider.provider_code` 全局唯一；逻辑删除后不复用该编码。
- 同一供应商下的 `ai_model.model_code` 全局唯一；逻辑删除后不复用该编码。
- 创建或更新模型时，目标供应商必须存在。
- 删除供应商前，若仍有未删除模型，则拒绝删除。
- 已启用的模型必须归属已启用的供应商。
- 将一个模型设为默认时，在同一事务内清除其余模型的默认标记；禁用或删除默认模型
  不会自动挑选替代默认模型。

服务层负责这些规则，Controller 不直接访问 Mapper。现有对运行时可用模型的查询仅
返回供应商和模型均启用的配置，避免某个已禁用供应商导致整批查询失败。

## 凭据安全

新增 `AiCredentialCipher`，以环境变量配置的 AES-GCM 主密钥加密 API Key。密文将
以包含随机 nonce 的版本化字符串保存到 `api_key_ciphertext`；每次新建或替换密钥时
递增 `credential_version`。默认的 `AiCredentialResolver` 使用同一组件解密，供
模型适配器在实际调用前使用。

主密钥不存在、格式不合法，或密文无法解密时，服务拒绝敏感写入或模型调用，且错误
与日志不得包含 API Key、密文或主密钥。为了避免静默降级，任何代码路径都不得将
明文 API Key 保存到数据库。

## 测试与验收

通过单元测试覆盖：

- 供应商与模型的创建、读取、更新、逻辑删除及排序。
- 编码唯一性、模型供应商存在性、删除供应商的关联约束和启用约束。
- 默认模型的互斥行为。
- API Key 的 AES-GCM 往返解密、错误密钥拒绝，以及响应对象不含密钥或密文。

通过 MVC 集成测试覆盖 admin Controller 的请求校验、成功响应与所需权限声明。
执行 `mvn verify` 作为全工程验证。
