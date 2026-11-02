# 知识库后端模块设计

- 日期：2026-10-30
- 状态：待实现
- 范围：仅后端，不包含前端页面或前端工程代码

## 1. 目标与范围

新增知识库后端能力，使用户可以创建个人知识库、上传文件或录入文本，系统异步完成内容解析、分块和向量化，并在聊天请求中显式选择一个或多个知识库进行检索增强生成（RAG）。

首期目标：

- 每个知识库绑定一个 Embedding 模型；不同知识库可以使用不同模型和不同向量维度。
- 支持 PDF、TXT、DOC、DOCX 文件，以及手动录入的文本/Markdown。
- 使用现有文件模块保存和读取文件，使用 PostgreSQL + pgvector 保存知识库内容和向量。
- 复用现有 AI 供应商配置体系增加 Embedding 能力。
- 通过异步任务完成索引，支持失败重试、应用重启恢复和换模型重建索引。
- 聊天消息显式传入 `knowledgeBaseIds`；未传入时保持普通聊天行为。
- 适配现有流式聊天和重新生成功能，并返回可追溯的知识来源。

明确不在本期范围内：

- 前端页面、前端组件和前端工程代码；
- 知识库共享成员和团队权限；
- 网页抓取、OCR、图片识别；
- 消息队列和独立索引服务；
- 独立向量数据库；
- 关键词/混合检索、Rerank 模型；
- 会话自动绑定知识库。

## 2. 现有项目约束

当前项目是 Spring Boot 多模块后端：

- MySQL 是主业务数据库，现有 Flyway、MyBatis-Plus 和聊天/文件/AI 配置表均使用 MySQL。
- PostgreSQL 已通过 `postgresqlDataSource` 和 `postgresqlJdbcTemplate` 独立接入，但当前没有参与 Flyway 或 MyBatis 链路。
- `ai-platform-file` 已提供 MinIO 文件存储、文件归属校验和 PDF/TXT 内容读取能力；DOC/DOCX 当前仅存储，需要新增 Apache POI 文本提取。
- `ai-platform-ai` 当前只抽象了流式聊天模型，供应商适配器包括 OpenAI 兼容接口和 Ollama。
- `ai-platform-chat` 在 `AiChatGatewayImpl` 中将聊天历史和文件内容转换为 `AiChatRequest`，再交给 AI 上下文预算和模型调用链路。

知识库设计遵循这些边界，不让文件模块感知知识库，也不让 AI 模块依赖知识库。

## 3. 模块与依赖

新增 `ai-platform-knowledge` 模块，依赖关系如下：

```text
ai-platform-common
      ↑
ai-platform-ai       ai-platform-file
      ↑                    ↑
      └──── ai-platform-knowledge
                    ↑
             ai-platform-chat
                    ↑
             ai-platform-boot
```

职责边界：

- `ai-platform-ai`：Embedding 模型配置快照、模型路由、供应商适配和 Embedding 调用抽象。
- `ai-platform-file`：文件上传、对象存储、文件读取和文件归属校验。
- `ai-platform-knowledge`：知识库、文档、分块、向量、索引任务、检索和知识库 REST 接口。
- `ai-platform-chat`：接收聊天请求中的知识库 ID，调用检索服务，将结果注入现有 AI 上下文并持久化消息关联。
- `ai-platform-boot`：引入新模块、扫描 Bean，并配置 PostgreSQL Flyway。

`ai-platform-ai` 不依赖 `ai-platform-knowledge`，避免循环依赖。知识库只依赖 AI 模块定义的 Embedding 接口，不直接使用具体供应商 SDK。

## 4. Embedding 模型设计

### 4.1 AI 模型表

保留现有 `ai_model.model_type` 字段，并新增：

```sql
embedding_dimension int null
```

字段含义不同：

- `model_type = CHAT` 或 `EMBEDDING` 表示模型能力类别；
- `embedding_dimension` 表示 Embedding 模型输出向量的长度。

校验规则：

- `CHAT` 模型的 `embedding_dimension` 必须为空；
- `EMBEDDING` 模型的 `embedding_dimension` 必须为正整数；
- 只有 `EMBEDDING` 模型可以被知识库绑定；
- 供应商和模型必须处于启用状态；
- Embedding 调用后校验实际向量长度必须等于配置值。

模型配置变更规则：

- 被知识库引用的 Embedding 模型不能直接修改向量维度；
- 模型代码、维度或实际向量语义发生变化时，创建新模型记录或通过知识库换模流程生成新索引版本；
- `config_version` 参与索引版本和调用审计，避免旧向量与新模型混用。

### 4.2 AI 抽象

在 `ai-platform-ai` 增加独立抽象：

```java
public interface AiEmbeddingService {
    List<float[]> embed(Long modelId, List<String> texts);
}
```

供应商层增加 `EmbeddingProvider`，与现有只负责流式聊天的 `ModelProvider` 分离。OpenAI、DeepSeek、Qwen 等 OpenAI 兼容供应商复用兼容协议；Ollama 使用本地 Embedding 模型。具体供应商不可用、模型类型错误和向量维度错误转换为稳定的知识库错误码。

`AiModelConfigSnapshot` 增加模型类型、Embedding 维度和配置版本，聊天模型的现有路由和调用行为保持不变。

现有后台 AI 模型接口同步扩展：

- `AiModelUpsertRequest` 增加 `embeddingDimension`；
- 模型管理响应增加 `embeddingDimension`；
- 创建和修改时按 `modelType` 校验该字段；
- 设置默认聊天模型时只允许 `CHAT` 模型参与；
- 面向聊天的可用模型列表过滤 `EMBEDDING` 模型；
- 删除或禁用被知识库引用的 Embedding 模型时返回资源冲突，换模必须通过知识库重建流程完成。

## 5. 数据模型

知识库领域数据放在 PostgreSQL，以便文档分块和向量检索处于同一数据库事务边界。用户、文件和聊天消息仍保留在 MySQL；跨数据库只保存业务 ID，不建立跨库外键。

### 5.1 `knowledge_base`

```text
id                    bigint primary key
user_id               bigint not null
name                  varchar(100) not null
description           text
embedding_model_id    bigint not null
active_index_version  bigint null
status                varchar(20) not null       -- ACTIVE / ARCHIVED
index_status          varchar(20) not null       -- READY / REINDEXING / PARTIAL
create_time           timestamp not null
update_time           timestamp not null
deleted               boolean not null default false
```

同一用户下知识库名称唯一。首期只根据 `user_id` 做归属校验，不创建成员表；未来增加共享时新增 `knowledge_base_member`，不改变文档和向量结构。

索引：

```text
(user_id, status, deleted, update_time)
(user_id, name) unique
```

### 5.2 `knowledge_document`

```text
id                    bigint primary key
knowledge_base_id     bigint not null
source_type           varchar(20) not null       -- FILE / TEXT
file_id               bigint null
title                 varchar(255) not null
raw_content           text null
content_hash          varchar(64) not null
status                varchar(20) not null       -- PENDING / INDEXING / READY / FAILED
active_index_version  bigint null
chunk_count           int not null default 0
error_code            varchar(64) null
error_message         text null
create_time           timestamp not null
update_time           timestamp not null
deleted               boolean not null default false
```

约束：

- `FILE` 文档必须有 `file_id`；`TEXT` 文档必须有 `raw_content`；
- 同一知识库内同一文件只能关联一次；
- 文件文档不复制原始文件内容，索引时通过 `FileContentService` 读取；
- 手动文本保存到 `raw_content`；
- 规范化文本计算 `content_hash`，内容未变化时不重复生成向量。

### 5.3 `knowledge_chunk`

```text
id                    bigint primary key
document_id           bigint not null
chunk_index           int not null
content               text not null
token_count           int not null
metadata              jsonb null
create_time           timestamp not null
```

`metadata` 首期保存来源类型、标题和分块序号；精确 PDF 页码待后续解析器支持后再增加。唯一约束为 `(document_id, chunk_index)`。

### 5.4 `knowledge_chunk_embedding`

```text
id                    bigint primary key
chunk_id              bigint not null
knowledge_base_id     bigint not null
embedding_model_id    bigint not null
model_version         int not null
dimension             int not null
index_version         bigint not null
embedding             vector not null
create_time           timestamp not null
```

由于不同知识库可能使用不同维度，向量列使用不固定维度的 pgvector `vector` 类型。查询时必须同时按知识库、模型 ID、索引版本和维度过滤，禁止跨模型直接比较向量。

初期轻量规模可先使用 pgvector 精确距离查询；在实际模型和数据规模稳定后，为常用模型/维度建立带模型条件的部分 HNSW 索引。不同模型的相似度分数不跨模型直接比较。

### 5.5 `knowledge_index_task`

```text
id                    bigint primary key
document_id           bigint not null
index_version         bigint not null
status                varchar(20) not null       -- PENDING / RUNNING / SUCCESS / FAILED
attempt_count         int not null default 0
next_retry_at         timestamp null
locked_at             timestamp null
last_error_code       varchar(64) null
last_error_message    text null
started_at            timestamp null
finished_at           timestamp null
create_time           timestamp not null
update_time           timestamp not null
```

`document_id + index_version` 保证同一个文档版本不会重复建立任务。任务由 PostgreSQL 事务配合行锁或 `FOR UPDATE SKIP LOCKED` 领取，支持多实例部署时避免重复处理。

### 5.6 聊天关联表

由于知识库按消息显式选择，在 MySQL 的聊天迁移中新增：

```text
chat_message_knowledge_base
- message_id
- knowledge_base_id
- create_time
```

如果返回知识来源，新增：

```text
chat_message_knowledge_source
- message_id
- knowledge_base_id
- document_id
- chunk_id
- chunk_index
- matched_text
- score
- create_time
```

这些表不复制知识库正文，只保存消息与检索结果的业务引用。

## 6. 索引流水线

```text
创建文件/文本
      ↓
保存 knowledge_document = PENDING
      ↓
创建 knowledge_index_task = PENDING
      ↓
worker 领取任务
      ↓
读取并解析内容
      ↓
规范化与计算 hash
      ↓
按段落和标题切分
      ↓
批量调用知识库绑定的 Embedding 模型
      ↓
写入 chunk 和 embedding
      ↓
文档状态 = READY
```

首期使用应用内有界线程池，不引入 MQ。任务表承担持久化、重试和重启恢复职责。默认自动重试 3 次；手动重试时将失败任务重新置为 `PENDING`。

解析能力：

- TXT：UTF-8 读取；
- PDF：复用 PDFBox；
- DOC/DOCX：Apache POI 提取文本；
- TEXT：直接处理文本或 Markdown。

切分策略初值：每块约 500 Token，相邻分块重叠约 50 Token，优先按标题和段落边界切分。切分器和 Token 估算器保持独立，便于后续按模型调整。

### 6.1 换模型

知识库换模型时创建新的 `index_version`，旧版本保持活动状态：

```text
READY → REINDEXING → SWITCHING → READY
```

流程：

1. 校验新模型为启用的 Embedding 模型；
2. 生成新版本并为所有有效文档创建任务；
3. 后台使用新模型重建分块向量；
4. 所有必需文档成功后，原子更新 `active_index_version`；
5. 删除旧版本向量；
6. 失败时保留旧版本，知识库恢复为可检索状态并记录失败信息。

不同版本不混合检索。

## 7. 检索与聊天接入

聊天请求新增：

```json
{
  "content": "公司的报销标准是什么？",
  "fileIds": [],
  "knowledgeBaseIds": [12, 15],
  "modelId": 3
}
```

检索流程：

1. 通过当前用户校验知识库归属和 `ACTIVE` 状态；
2. 读取每个知识库的活动模型和索引版本；
3. 按 Embedding 模型分组；
4. 每个模型只生成一次问题向量；
5. 只查询对应知识库、模型、维度和活动版本的分块；
6. 每个知识库先取自己的 Top-K；
7. 使用排名而不是原始距离合并不同模型的结果；
8. 按聊天模型上下文预算裁剪；
9. 将知识内容作为有边界的参考上下文注入现有 AI 消息链路。

不同模型的余弦距离不直接跨模型比较。默认每个知识库 Top-K 为 8，每个文档最多保留 3 个分块，最终结果受现有 `TokenBudgetManager` 限制。具体阈值作为知识库检索配置常量，首期不开放用户配置。

知识上下文必须明确标记为不可信参考资料，文档中的指令不能覆盖系统指令或用户问题指令。

知识库检索发生在 SSE 建立前：

- 校验、模型选择或检索失败时返回普通 JSON 错误；
- SSE 建立后不再新增知识库错误分支；
- 未选择知识库时完全跳过检索，保持当前聊天逻辑；
- 选中的知识库没有可用分块时返回 `KNOWLEDGE_BASE_NOT_READY`，不静默降级为普通聊天。

重新生成时从 `chat_message_knowledge_base` 恢复源用户消息的知识库选择，不要求客户端重新提交 ID。

完成事件可以增加：

```json
{
  "knowledgeSources": [
    {
      "knowledgeBaseId": 12,
      "documentId": 101,
      "documentTitle": "报销制度",
      "chunkId": 9001,
      "chunkIndex": 3,
      "matchedText": "差旅费用应在出差结束后提交...",
      "score": 0.86
    }
  ]
}
```

来源信息同时写入 `chat_message_knowledge_source`，不写入助手正文。

## 8. REST 接口

### 8.1 知识库

```text
POST   /api/knowledge-bases
GET    /api/knowledge-bases?page=1&size=20
GET    /api/knowledge-bases/{id}
PUT    /api/knowledge-bases/{id}
DELETE /api/knowledge-bases/{id}
PUT    /api/knowledge-bases/{id}/embedding-model
POST   /api/knowledge-bases/{id}/reindex
```

创建请求：

```json
{
  "name": "产品文档",
  "description": "产品相关资料",
  "embeddingModelId": 8
}
```

换模型请求：

```json
{
  "embeddingModelId": 12
}
```

详情响应包含知识库基本信息、绑定模型、文档数量、已就绪文档数量、分块数量和索引状态。

### 8.2 文档

```text
POST   /api/knowledge-bases/{id}/documents/files
POST   /api/knowledge-bases/{id}/documents/text
GET    /api/knowledge-bases/{id}/documents?page=1&size=20
GET    /api/knowledge-bases/{id}/documents/{documentId}
POST   /api/knowledge-bases/{id}/documents/{documentId}/retry
DELETE /api/knowledge-bases/{id}/documents/{documentId}
```

文件接口接收 `multipart/form-data`，由知识库服务调用现有文件服务完成上传和归属校验。手动文本请求：

```json
{
  "title": "报销制度",
  "content": "# 报销范围\n\n差旅报销需要..."
}
```

文档状态和最近一次错误信息通过文档详情和列表接口返回。

### 8.3 聊天

在现有 `ChatRequestDTO` 增加：

```text
knowledgeBaseIds: List<Long>
```

设置最大数量限制，校验每个 ID 的归属、状态和可检索性。`ChatRegenerateDTO` 不增加该字段，因为重新生成使用已持久化的用户消息关联。

## 9. 错误码与异常行为

```text
KNOWLEDGE_BASE_NOT_FOUND
KNOWLEDGE_BASE_MODEL_INVALID
KNOWLEDGE_BASE_REINDEXING
KNOWLEDGE_DOCUMENT_NOT_FOUND
KNOWLEDGE_DOCUMENT_PARSE_FAILED
KNOWLEDGE_DOCUMENT_INDEX_FAILED
KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE
KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH
KNOWLEDGE_RETRIEVAL_FAILED
KNOWLEDGE_BASE_NOT_READY
```

建议 HTTP 映射：

- 400：参数、类型或模型配置错误；
- 404：知识库或文档不存在，或不属于当前用户；
- 409：重复关联、正在重建或状态冲突；
- 422：文件解析或向量维度不符合预期；
- 503：Embedding 服务或 PostgreSQL/pgvector 不可用。

补偿与恢复：

- 文件上传成功但 PostgreSQL 文档创建失败时，补偿删除已上传文件；
- 文档和索引任务在同一 PostgreSQL 事务中创建；
- 向量写入完成后才将文档标记为 `READY`；
- 应用启动时将超时 `RUNNING` 任务恢复为 `PENDING`；
- 索引失败只影响当前文档，换模型失败时保留旧活动版本。

所有用户输入使用参数化 SQL。原始文件和手动文本遵循现有文件大小约束；手动文本需要独立设置长度上限，避免无限制写入和 Embedding 成本失控。

## 10. 测试策略

### 10.1 单元测试

- 知识库创建和 Embedding 模型类型/维度校验；
- 文档来源字段和内容约束；
- PDF、TXT、DOC、DOCX 和文本解析；
- 分块边界、Token 估算和重叠策略；
- 不同知识库按模型分组查询；
- 向量长度校验；
- 任务状态流转、重试次数和超时恢复；
- 用户归属校验；
- 换模型期间的索引版本切换。

### 10.2 PostgreSQL 集成测试

使用启用 pgvector 的 PostgreSQL 测试实例验证：

- PostgreSQL Flyway 迁移和扩展；
- 不同维度向量共存；
- 相似度查询和活动版本过滤；
- 分块、向量和文档状态的事务一致性；
- 多任务并发领取和幂等性；
- 新旧索引版本原子切换。

Embedding 调用使用确定性测试实现，避免测试依赖真实供应商和网络。

### 10.3 接口与聊天集成测试

- 知识库 CRUD 和资源归属；
- 文件上传、文本录入、状态查询、重试和删除；
- 模型切换与重建接口；
- 聊天请求携带 `knowledgeBaseIds` 的检索注入；
- 多模型知识库同时检索；
- 未选择知识库时保持普通聊天；
- 重新生成恢复原始知识库选择；
- 检索来源进入完成事件和持久化记录；
- 统一错误响应及 SSE 建立前的失败行为。

## 11. 实施顺序

1. 新增 `ai_model.embedding_dimension` 和 Embedding 模型校验；
2. 增加 `AiEmbeddingService`、Embedding provider 和供应商适配；
3. 新增知识库模块、PostgreSQL Flyway 和 Repository；
4. 实现文档解析、分块和索引任务 worker；
5. 实现知识库和文档 REST 接口；
6. 实现多模型向量检索和索引版本切换；
7. 扩展聊天请求、消息关联和来源持久化；
8. 接入现有流式聊天链路；
9. 补齐单元、PostgreSQL 集成和接口测试。

实现完成的验收标准是：用户可以通过后端接口创建绑定不同 Embedding 模型的知识库，分别完成文档索引，并在同一条聊天请求中选择多个知识库进行检索；不同模型的向量不会混用，失败任务可重试，换模期间旧索引仍可用，且普通聊天行为不受影响。
