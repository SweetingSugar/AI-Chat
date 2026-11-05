# 知识库文本文档正文读取接口设计

## 目标

在知识库模块新增接口：

```http
GET /api/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/content
```

该接口仅读取 `sourceType = TEXT` 的知识文档正文，并沿用项目现有的统一响应格式。

## 接口契约

成功响应：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "documentId": 18,
    "title": "请假制度",
    "content": "第一条……"
  }
}
```

`content` 原样返回文档保存的 `raw_content`，保留原始换行、空格和其他文本内容，不执行规范化、分块或文件解析。

## 架构与数据流

扩展现有 `KnowledgeBaseService`，增加读取文档正文的业务方法，由 `KnowledgeBaseController` 调用。新增不可变的 `KnowledgeDocumentContentVO`，字段为：

- `documentId`：文档 ID；
- `title`：文档标题；
- `content`：TEXT 文档原始正文。

请求处理顺序：

1. Controller 从 `CurrentUserProvider` 获取当前用户 ID；
2. Service 校验当前用户拥有且未删除的知识库；
3. Service 校验文档存在、未删除，并且属于路径中的知识库；
4. 文档 `sourceType` 不是 `TEXT` 时抛出 `KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`；
5. 校验 `rawContent` 非空；
6. 返回文档 ID、标题和原始正文，Controller 使用 `ApiResponse.success` 包装。

现有 `KnowledgeDocumentContentReader` 继续服务于索引流程，保留其对 FILE 文档的读取能力；本接口不复用其 FILE 分支，避免将接口范围扩大到文件文档。

## 错误处理

新增 `ErrorCode.KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`，HTTP 状态为 `422 UNPROCESSABLE_ENTITY`，默认消息为 `Knowledge document content is not supported`。

错误场景：

- 知识库不存在、已删除或不属于当前用户：`KNOWLEDGE_BASE_NOT_FOUND`；
- 文档不存在、已删除或不属于指定知识库：`KNOWLEDGE_DOCUMENT_NOT_FOUND`；
- 文档不是 TEXT 来源：`KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`；
- TEXT 文档正文为空：现有 `KNOWLEDGE_DOCUMENT_PARSE_FAILED`。

所有错误通过现有 `GlobalExceptionHandler` 统一转换为 `ApiResponse`，不新增 Controller 层异常处理逻辑。

## 实现范围

修改：

- `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/controller/KnowledgeBaseController.java`
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/KnowledgeBaseService.java`
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/service/impl/KnowledgeBaseServiceImpl.java`

新增：

- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/vo/KnowledgeDocumentContentVO.java`
- 对应 Service 和 Controller 测试覆盖。

不修改数据库结构、Mapper XML、索引流程或 FILE 文档读取行为。

## 测试设计

Service 单元测试覆盖：

1. TEXT 文档成功返回 ID、标题和未修改的原始正文；
2. 路径知识库不拥有该文档时返回 `KNOWLEDGE_DOCUMENT_NOT_FOUND`；
3. FILE 文档返回 `KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED`；
4. TEXT 文档 `rawContent` 为空时返回 `KNOWLEDGE_DOCUMENT_PARSE_FAILED`。

Controller 测试覆盖：

1. 新 GET 路径返回 HTTP 200；
2. 响应包含统一字段和 `data.documentId/title/content`；
3. Service 收到当前用户 ID、知识库 ID 和文档 ID。

验证命令：

```bash
mvn -pl ai-platform-knowledge -am -DskipTests=false test
```
