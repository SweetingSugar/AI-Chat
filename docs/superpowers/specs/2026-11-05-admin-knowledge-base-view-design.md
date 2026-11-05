# 管理员查看知识库及文档设计

## 背景

现有知识库模块提供了按当前用户查询知识库、文档和文本内容的接口，但管理员模块还不能查看全体用户的知识库数据。知识库使用独立的 PostgreSQL/pgvector 数据源，管理员接口需要保持这一数据源边界，并沿用后台接口的权限控制方式。

## 目标与范围

新增管理员只读能力：

- 分页查看所有未逻辑删除的知识库；
- 按知识库分页查看所有未逻辑删除的文档；
- 查看文本类型文档的原始内容；
- 展示知识库所属用户 ID；
- 使用独立的管理员权限码保护接口。

不在本次范围内：

- 管理员创建、编辑、删除知识库或文档；
- 管理员重试索引、切换向量模型或修改索引状态；
- 直接返回文件文档的二进制或解析内容；
- 恢复已逻辑删除的知识库或文档；
- 增加用户昵称等跨 MySQL/PostgreSQL 的关联查询。

## 方案选择

采用“知识库模块提供管理员只读服务，管理员模块提供 HTTP 控制器”的方案。

- 知识库模块负责 PostgreSQL Mapper、仓储和知识库业务边界，避免 admin 模块重复 SQL 或误用默认 MySQL 数据源；
- admin 模块负责 `/api/admin/**` 路由和 Sa-Token 权限注解，符合现有后台接口组织方式；
- 不把管理员查询方法加入面向当前用户的 `KnowledgeBaseService`，避免混淆用户所有权查询和全量查询；
- 不在管理员接口中复用带用户归属校验的普通用户控制器。

## API 设计

新增 `GET /api/admin/knowledge-bases`：

- 权限：`knowledge:read`；
- 查询参数：`page` 默认 1，`size` 默认 20，服务层将 page 归一化为不小于 1、size 限制为 1–100；
- 响应：`ApiResponse<PageResult<KnowledgeBaseAdminVO>>`；
- 查询条件：`knowledge_base.deleted = false`；保留 `ACTIVE` 和 `DISABLED` 状态；
- 排序：`update_time desc, id desc`，与用户侧知识库列表一致。

`KnowledgeBaseAdminVO` 包含现有知识库展示字段，并额外包含 `userId`：

- `id`、`userId`、`name`、`description`；
- 当前/待切换 embedding model ID 与版本；
- `activeIndexVersion`、`reindexing`、`status`；
- `createTime`、`updateTime`。

新增 `GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents`：

- 权限：`knowledge:read`；
- 查询参数和分页规则同知识库列表；
- 先确认知识库存在且 `deleted = false`，再查询文档；
- 响应：`ApiResponse<PageResult<KnowledgeDocumentVO>>`；
- 文档查询条件：`knowledge_document.knowledge_base_id` 匹配路径参数且 `deleted = false`；
- 排序：`update_time desc, id desc`；
- 文档对象沿用既有字段，包括标题、来源类型、文件 ID、索引状态、分块数、错误信息和时间。

新增 `GET /api/admin/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/content`：

- 权限：`knowledge:read`；
- 先校验知识库未删除，再校验文档存在、未删除且属于该知识库；
- `TEXT` 文档返回既有 `KnowledgeDocumentContentVO`；
- `FILE` 文档复用 `KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`，管理员可通过文档返回的 `fileId` 使用现有 `/api/admin/files/{fileId}` 或 `/presigned-url` 接口查看文件；
- 路径中的知识库与文档不匹配时统一返回资源不存在错误，避免泄露跨知识库资源信息。

所有接口均只读，不改变用户侧 `/api/knowledge-bases/**` 的请求和响应契约。

## 模块与数据访问设计

### knowledge 模块

新增管理员只读服务，例如 `KnowledgeBaseAdminService` 及其实现，提供：

- `PageResult<KnowledgeBaseAdminVO> page(int page, int size)`；
- `PageResult<KnowledgeDocumentVO> documentPage(Long knowledgeBaseId, int page, int size)`；
- `KnowledgeDocumentContentVO documentContent(Long knowledgeBaseId, Long documentId)`。

在知识库仓储/Mapper 中增加不带 `user_id` 条件的知识库分页查询、总数查询和未删除知识库查询。文档分页和按 ID 查询复用现有 PostgreSQL Mapper 能力，但由管理员服务完成知识库存在性、文档归属和逻辑删除校验。

新增管理员 VO，而不是修改用户侧 `KnowledgeBaseVO`，防止普通用户接口意外暴露 `userId`。

### admin 模块

- `ai-platform-admin/pom.xml` 增加对 `ai-platform-knowledge` 的直接依赖；
- 新增管理员知识库控制器，路径前缀为 `/api/admin/knowledge-bases`；
- 每个端点添加 `@SaCheckPermission("knowledge:read")`；
- 控制器只负责参数接收、调用管理员服务和包装 `ApiResponse`，不直接访问 Mapper。

### 权限迁移

新增 `ai-platform-admin/src/main/resources/db/migration/V12__admin_knowledge_permission.sql`，使用 `insert ignore` 注册：

- 权限名称：`Knowledge base read`；
- 权限码：`knowledge:read`；
- 权限类型：菜单/接口权限类型 3；
- 根权限 `parent_id = 0`；
- 排序值：`203`，紧接现有文件权限 `200`–`202` 且不与已有权限冲突。

## 错误与安全边界

- 未登录或缺少 `knowledge:read` 权限由现有 Sa-Token 机制处理；
- 不存在或已删除的知识库统一返回 `KNOWLEDGE_BASE_NOT_FOUND`；
- 不存在、已删除或不属于路径知识库的文档统一返回 `KNOWLEDGE_DOCUMENT_NOT_FOUND`；
- 非文本内容读取复用 `KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`；
- 不向管理员接口传入或依赖当前用户 ID；
- 查询只过滤逻辑删除记录，不对已删除数据做恢复或额外暴露；
- 不引入跨数据源 Join，所属用户仅返回 `userId`。

## 测试策略

1. **管理员控制器测试**
   - 验证三个路径和分页参数被正确转发；
   - 验证分页响应中的知识库 `userId` 和文档字段；
   - 验证内容接口响应结构。
2. **管理员服务测试**
   - 验证 page/size 归一化和上限；
   - 验证知识库不存在或已删除时拒绝文档查询；
   - 验证跨知识库文档访问被拒绝；
   - 验证文本内容可读、文件文档被拒绝；
   - 验证管理员查询不调用当前用户归属查询。
3. **Mapper XML 测试**
   - 验证新增 SQL 使用知识库 PostgreSQL 表；
   - 验证知识库和文档查询包含 `deleted = false`、稳定排序和分页参数。
4. **构建验证**
   - 运行知识库模块和管理员模块测试；
   - 运行聚合 Maven 测试，确认 PostgreSQL 专用 Mapper 仍由知识库数据源会话工厂加载。

## 文档更新

在 `docs/frontend-api-integration.md` 增加管理员知识库接口、权限和响应字段说明，并在 `docs/backend-development-guide.md` 的模块/接口索引中补充管理员知识库查询能力。
