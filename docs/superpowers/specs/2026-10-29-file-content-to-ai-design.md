# 文件内容传入 AI 模型设计

- 日期：2026-10-29
- 状态：设计稿
- 目标模块：`ai-platform-file`、`ai-platform-chat`、`ai-platform-ai`
- 关联模块：`ai-platform-boot`、`ai-platform-admin`

## 1. 背景与目标

当前项目已经支持文件上传、MinIO 存储以及聊天消息与文件 ID 的关联，但 `fileIds` 只用于附件持久化和历史展示，模型调用链仍然只接收纯文本消息。

本次实现需要让模型实际分析聊天附件，首期支持：

1. `text/plain` 文本文件。
2. `application/pdf` PDF 文件。
3. `image/jpeg`、`image/png`、`image/gif` 图片。
4. 所有当前 provider 复用统一的文本/图片消息表示。
5. 文件在首次上传的用户消息中生效，并在后续多轮对话和重新生成时从历史消息恢复。
6. 保留现有聊天请求格式，继续通过 `fileIds` 传递附件。

处理策略为：

- TXT：服务端读取并转为文本。
- PDF：服务端提取文本后传给模型。
- 图片：服务端读取原始字节，以图片内容块传给支持视觉的模型。
- DOC/DOCX：本期不传给模型，返回明确的不支持错误。

## 2. 方案选择

### 2.1 推荐方案：服务端解析 + 统一多模态消息

服务端从私有 MinIO 读取文件内容。文本和 PDF 统一转换为文本，图片保留字节并转换为 LangChain4j 的图片内容。聊天模块负责附件与 AI 消息之间的转换，AI 模块只接收与文件存储无关的通用内容对象。

```text
ai-platform-boot
 ├── ai-platform-chat
 │    ├── ai-platform-ai
 │    └── ai-platform-file
 └── ai-platform-ai
      └── LangChain4j provider adapters

ai-platform-chat ──> FileContentService ──> ObjectStorage/MinIO
ai-platform-chat ──> AiChatService ──> ModelProvider
```

该方案不依赖模型 provider 访问私有 MinIO URL。TXT/PDF 可以跨 provider 工作，图片保留真正的视觉输入能力。具体模型是否支持图片由模型能力配置控制。

### 2.2 未采用的方案

- **只传文件 URL**：模型服务端可能无法访问私有 MinIO，且不同 provider 对图片 URL、PDF URL 的支持不一致；预签名 URL 还存在过期和泄露风险。
- **所有文件都转为 OCR/纯文本**：跨 provider 兼容性较好，但会损失图片中的图表、布局和非文字视觉信息。
- **每个 provider 单独构造原生请求**：能够覆盖更多 provider 特性，但会重复实现消息转换，扩大维护范围；PDF 仍需要 provider-specific 处理。

## 3. 范围与非目标

### 3.1 本期范围

- 读取用户拥有且状态为 `ACTIVE` 的附件。
- 解析 TXT 和 PDF 文本。
- 将 JPEG、PNG、GIF 作为图片内容传给视觉模型。
- 将历史用户消息中的附件恢复到模型上下文。
- 重新生成时沿用原用户消息附件。
- 为模型增加视觉能力配置。
- 更新上下文预算、错误处理、测试和接口文档。

### 3.2 本期非目标

- 不支持 DOC/DOCX 内容解析。
- 不实现 OCR。
- 不实现扫描 PDF 的页面识别。
- 不新增 provider-specific 的文件上传接口。
- 不改变文件上传接口和聊天请求 JSON 结构。
- 不把文件内容写回聊天消息正文或数据库。
- 不向模型暴露 MinIO bucket、object key 或内部存储地址。

## 4. 数据流

### 4.1 新消息

1. 客户端上传文件并取得文件 ID。
2. 客户端调用现有聊天接口，在 JSON 中传入 `content` 和 `fileIds`。
3. `ChatServiceImpl` 使用现有文件访问边界校验文件归属和状态。
4. `ChatGenerationService` 保存用户消息并绑定 `chat_message_file` 关系。
5. `ChatMessageService.context()` 查询消息历史，同时返回每条用户消息关联的文件 ID。
6. `AiChatGatewayImpl` 调用 `FileContentService` 读取并解析附件。
7. 聊天模块将消息文本和解析后的附件转换为 AI 内容对象。
8. `ContextBuilder` 按文本和附件估算上下文成本并裁剪完整消息。
9. `MessageConverter` 将内容对象转换为 LangChain4j 文本/图片内容。
10. 所有 provider 通过现有流式模型接口发起调用。

### 4.2 后续追问和重新生成

历史消息上下文保留文件 ID。每次生成时，仍从文件服务读取当前上下文中消息的附件内容：

- 后续追问无需再次上传或传入 `fileIds`。
- 如果历史消息因上下文预算被裁剪，其附件也随消息一起被裁剪。
- 重新生成从原用户消息恢复附件，不依赖重新生成请求中的 `fileIds`。

文件内容只在模型请求期间保存在内存中，不新增数据库字段保存解析文本或图片字节。

## 5. 模块设计

### 5.1 `ai-platform-file`

文件模块继续拥有文件元数据、权限、对象存储和文件状态逻辑，新增面向内部调用的内容读取边界。

```java
public interface FileContentService {
    List<FileContent> readOwnedActive(Long userId, List<Long> fileIds);
}
```

```java
public record FileContent(
        Long fileId,
        String fileName,
        String contentType,
        String text,
        byte[] bytes) {
}
```

实现要求：

- 先按 `userId` 查询并校验文件记录。
- 只允许读取 `ACTIVE` 文件。
- 使用已存在的 `ObjectStorage.get(bucketName, objectKey)` 读取对象。
- 不让调用方接触 bucket 名称和 object key。
- `text/plain` 读取为 UTF-8 文本。
- `application/pdf` 使用 PDFBox 提取文本。
- 图片读取为字节数组。
- 其他 MIME 类型抛出明确的文件内容类型异常。
- 不使用 `FileService.download()` 作为模型读取入口，因为该方法面向 HTTP 下载并依赖当前登录用户上下文；模型读取需要显式使用 `userId` 并支持批量处理。

PDF 提取结果为空时返回明确的空内容错误。解析过程中的存储读取失败、PDF 损坏和不支持类型都必须保留业务错误类型，不能静默转换成空字符串。

### 5.2 `ai-platform-chat`

扩展聊天上下文模型：

```java
public record ChatMessagePair(
        ChatMessageRole role,
        String content,
        List<Long> fileIds) {
}
```

`ChatMessageService.context()` 通过 `ChatMessageFileService` 获取历史消息的文件 ID，并按原消息顺序附加到 `ChatMessagePair`。

`AiChatGatewayImpl` 负责：

1. 将 `ChatMessagePair.fileIds()` 传给 `FileContentService`。
2. 将 `FileContent` 映射到 AI 模块的内容 DTO。
3. 保留用户文本和文件内容的顺序。
4. 在图片存在但模型不支持视觉时阻止 provider 调用。

聊天模块不直接操作 MinIO SDK，也不解析 PDF；文件内容读取和解析由文件模块封装。

### 5.3 `ai-platform-ai`

AI 消息从单一字符串扩展为内容列表，同时保留纯文本构造方式，避免无附件请求行为变化。

```java
public record AiMessageContentDTO(
        AiMessageContentType type,
        String text,
        String mediaType,
        byte[] data) {
}
```

内容类型：

- `TEXT`：普通用户文本、TXT 内容和 PDF 提取文本。
- `IMAGE`：图片媒体类型和原始字节。

`AiMessageDTO` 保留现有 `AiMessageDTO(AiMessageRole, String)` 兼容入口，并增加带内容列表的构造方式。`AiRequestValidator`、`ContextBuilder`、`TokenBudgetManager` 和用量估算逻辑同步支持内容列表。

`MessageConverter` 的转换规则：

- SYSTEM 和 ASSISTANT 保持文本消息转换。
- 无附件的 USER 保持 `UserMessage.from(String)` 行为。
- 带文本附件的 USER 将用户问题和提取文本组合成文本内容。
- 带图片的 USER 将文本内容和图片内容组合成多模态用户消息。
- 图片数据不写入日志、不写入 AI 调用审计表。

所有 provider 继续接收统一的 LangChain4j `ChatMessage` 列表，现有 `ModelProvider` 接口不增加文件模块依赖。

## 6. 模型视觉能力

在 `ai_model` 增加模型能力字段：

```sql
support_vision tinyint not null default 0 comment '是否支持图片输入'
```

同步扩展：

- `AiModel`。
- `AiModelUpsertRequest`。
- `AiModelDTO`。
- `AiModelManagementDTO`。
- 模型创建、更新、查询和配置快照逻辑。

校验规则：

- 只包含文本或 PDF 的请求不需要 `support_vision`。
- 包含图片的请求要求 `support_vision = 1`。
- `support_vision = 0` 时返回 `AI_VISION_NOT_SUPPORTED`，不调用 provider。
- provider 仍可能因具体模型配置错误拒绝图片请求；该错误通过现有 AI provider 错误链路返回。

该字段描述具体模型能力，而不是 provider 类型能力，因为同一个 provider 下的不同模型可能具有不同视觉能力。

## 7. 上下文预算与限制

继续沿用现有单消息最多 10 个文件和单文件最大 10 MB 的上传限制。

上下文预算处理：

- 普通文本按现有字符估算方式计算。
- TXT/PDF 提取文本按文本长度计入预算。
- 图片按保守的固定输入成本计入预算，避免只计算用户问题文本。
- 裁剪单位为完整 `AiMessageDTO`；消息关联的附件不能脱离消息单独保留。
- 当前用户消息不能因附件估算而被静默删除；如果其附件无法放入可用上下文，返回附件内容过大错误。
- 不对 PDF 或文本内容做无提示截断。

## 8. 错误处理

### 8.1 文件错误

- 文件不存在、非当前用户所有或状态非 `ACTIVE`：复用现有文件访问错误。
- MIME 类型不在本期支持范围：返回 `AI_FILE_TYPE_NOT_SUPPORTED`。
- PDF 无法解析：返回 `AI_FILE_PARSE_FAILED`。
- PDF 提取后无文本：返回 `AI_FILE_CONTENT_EMPTY`。
- MinIO 读取失败：返回文件读取错误，并结束当前生成任务。

### 8.2 模型错误

- 有图片但模型未开启视觉能力：返回 `AI_VISION_NOT_SUPPORTED`。
- provider 拒绝图片内容或请求失败：复用 `AI_PROVIDER_ERROR`，不泄露 provider 原始凭证或内部请求细节。
- 附件内容超出上下文可用空间：返回 `AI_FILE_CONTENT_TOO_LARGE`。

### 8.3 流式任务状态

附件解析发生在 provider 调用之前。解析失败时：

1. 不向 provider 发起请求。
2. 通过现有流式失败事件返回错误码和安全消息。
3. 将已创建的生成任务标记为 `FAILED`。
4. 保留用户消息和附件关联，便于用户修正模型或文件后重试。

## 9. 测试设计

### 9.1 文件模块

- TXT 成功读取并得到 UTF-8 文本。
- PDF 成功提取文本。
- JPEG、PNG、GIF 成功读取字节和 MIME 类型。
- DOC/DOCX 返回不支持错误。
- 非本用户文件、非 `ACTIVE` 文件被拒绝。
- MinIO 读取异常被转换为业务错误。
- 损坏 PDF 和空文本 PDF 返回对应错误。

### 9.2 聊天模块

- 新用户消息的附件进入 AI 上下文。
- 后续追问能恢复历史用户消息附件。
- 重新生成沿用原用户消息附件。
- 消息历史裁剪时附件与所属消息一起裁剪。
- 文件 ID 为空时保持现有纯文本行为。

### 9.3 AI 模块

- 旧的 `AiMessageDTO(role, content)` 仍生成相同纯文本消息。
- TXT/PDF 内容生成文本内容。
- 图片内容生成 LangChain4j 图片内容块。
- `TokenBudgetManager` 计入文本附件和图片成本。
- 视觉能力关闭时不调用 provider 并返回明确错误。
- 各 provider 适配器的纯文本回归测试通过。

### 9.4 集成验收

至少验证以下路径：

1. 上传 TXT，发起聊天，模型能够根据 TXT 内容回答。
2. 上传 PDF，发起聊天，模型能够根据 PDF 提取文本回答。
3. 使用 `support_vision = 1` 的模型上传图片，模型能够根据图片内容回答。
4. 首次上传文件后不再次传 `fileIds`，后续追问仍能引用该文件。
5. 使用不支持视觉的模型发送图片时，返回 `AI_VISION_NOT_SUPPORTED`，且 provider 没有收到请求。
6. 没有附件的既有纯文本聊天行为不变。

## 10. 文档更新

更新以下文档：

- `docs/backend-development-guide.md`：说明支持的 AI 文件类型、解析方式和模型视觉能力字段。
- `docs/frontend-api-integration.md`：说明 `fileIds` 在首次消息和后续多轮对话中的行为，以及图片模型配置要求。
- AI 模型管理接口说明：增加 `supportVision` 字段和视觉能力错误说明。

## 11. 验收标准

实现完成后应满足：

- 模型请求中不再只包含 `role + String content`，支持文本与图片内容。
- TXT 和 PDF 内容能够跨当前 provider 进入模型上下文。
- 支持视觉的模型能够收到图片实际内容，而不是依赖私有 MinIO URL。
- 历史消息附件和重新生成附件行为正确。
- 文件权限、状态和存储路径不会因 AI 内容读取而绕过现有安全边界。
- 解析失败、类型不支持、视觉能力不足和上下文超限均有明确错误。
- 现有无附件纯文本聊天和文件上传功能回归通过。
