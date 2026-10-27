# MinIO 文件上传功能设计

- 日期：2026-10-27
- 状态：设计稿
- 目标模块：`ai-platform-file`
- 关联模块：`ai-platform-chat`、`ai-platform-admin`、`ai-platform-boot`

## 1. 背景与目标

当前项目已经按 `common`、`system`、`ai`、`chat`、`admin` 和 `boot` 拆分，但没有文件上传或对象存储能力。需要新增基于 MinIO 的文件能力，支持：

1. MinIO 配置与连接管理。
2. 文件上传、下载和删除。
3. 文件元数据持久化。
4. 文件类型、大小和用户权限校验。
5. 临时预签名 URL。
6. 聊天消息与文件关联。
7. 管理员分页查询和删除文件。

本阶段只实现文件对象管理和聊天消息关联，不解析 PDF、Word、图片等文件内容，也不把文件内容注入 AI 上下文。

## 2. 方案选择

### 2.1 推荐方案：新增 `ai-platform-file`

新增独立文件模块，封装对象存储和文件元数据。`chat` 只依赖文件服务接口，不直接依赖 MinIO SDK。

```text
ai-platform-boot
 ├── ai-platform-file
 └── ai-platform-chat ──> ai-platform-file

ai-platform-file ──> MinIO
ai-platform-file ──> MySQL
```

### 2.2 未采用的方案

- 将全部逻辑放入 `ai-platform-chat`：会让通用文件能力和聊天业务耦合。
- 放入 `ai-platform-common`：`common` 当前只承载统一响应、异常、当前用户和基础配置，不应该包含文件业务实体、接口和数据库迁移。
- 放入 `ai-platform-ai`：文件对象存储不属于 AI 供应商或模型调用边界。

## 3. 模块结构

```text
ai-platform-file/
├── pom.xml
└── src/main/
    ├── java/com/example/aichat/file/
    │   ├── config/
    │   │   ├── FileStorageProperties.java
    │   │   └── MinioConfiguration.java
    │   ├── controller/
    │   │   ├── FileController.java
    │   │   └── AdminFileController.java
    │   ├── dto/
    │   │   └── FileQueryRequest.java
    │   ├── entity/
    │   │   └── FileMetadata.java
    │   ├── enums/
    │   │   └── FileStatus.java
    │   ├── exception/
    │   │   └── FileErrorCode.java
    │   ├── mapper/
    │   │   └── FileMetadataMapper.java
    │   ├── service/
    │   │   ├── FileService.java
    │   │   ├── FileAccessService.java
    │   │   └── impl/FileServiceImpl.java
    │   ├── storage/
    │   │   ├── ObjectStorage.java
    │   │   └── MinioObjectStorage.java
    │   └── vo/
    │       └── FileMetadataVO.java
    └── resources/
        └── db/migration/V7__file_schema.sql
```

`ObjectStorage` 是存储适配边界，只有 `MinioObjectStorage` 直接依赖 MinIO SDK。文件业务服务负责权限、状态和数据库，聊天模块不访问 bucket、object key 或 MinIO 客户端。

## 4. 配置设计

在 `ai-platform-boot/src/main/resources/application.yml` 增加：

```yaml
file:
  storage:
    type: minio
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    access-key: ${MINIO_ACCESS_KEY:minioadmin}
    secret-key: ${MINIO_SECRET_KEY:minioadmin}
    bucket: ${MINIO_BUCKET:ai-files}
    secure: ${MINIO_SECURE:false}
    presign-expire-seconds: ${MINIO_PRESIGN_EXPIRE_SECONDS:3600}
    max-file-size: ${FILE_MAX_SIZE:10485760}
    allowed-content-types:
      - image/jpeg
      - image/png
      - image/gif
      - application/pdf
      - text/plain
      - application/msword
      - application/vnd.openxmlformats-officedocument.wordprocessingml.document
```

同时配置 Spring Multipart 上限，防止请求在业务校验前占用过多资源：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${FILE_MAX_SIZE:10MB}
      max-request-size: ${FILE_MAX_REQUEST_SIZE:10MB}
```

生产环境必须通过环境变量传入 MinIO 凭证，不使用默认账号密码。MinIO bucket 默认设置为私有，应用启动时检查 bucket，不存在时创建。

`MinioConfiguration` 负责创建 `MinioClient`、绑定 `FileStorageProperties` 和注册 `ObjectStorage` Bean。`ai-platform-file` 引入官方 `io.minio:minio` 依赖，版本在父 POM 的依赖管理中统一维护。

## 5. 数据模型

### 5.1 文件元数据表

迁移文件：`ai-platform-file/src/main/resources/db/migration/V7__file_schema.sql`

```sql
create table if not exists file_metadata (
    id bigint not null auto_increment primary key comment '文件主键 ID',
    user_id bigint not null comment '上传用户 ID',
    bucket_name varchar(100) not null comment 'MinIO bucket',
    object_key varchar(500) not null comment 'MinIO object key',
    original_name varchar(255) not null comment '原始文件名',
    content_type varchar(150) not null comment '文件 MIME 类型',
    file_size bigint not null comment '文件大小，单位字节',
    file_hash varchar(128) null comment '文件摘要',
    status varchar(32) not null default 'ACTIVE' comment '文件状态',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    deleted tinyint not null default 0 comment '逻辑删除标记',
    unique key uk_file_object (bucket_name, object_key),
    key idx_file_user_time (user_id, create_time),
    key idx_file_status_time (status, create_time),
    key idx_file_content_type (content_type)
) comment = '文件元数据表';
```

文件状态：

- `ACTIVE`：可以查询、下载和关联聊天消息。
- `DELETING`：正在删除对象，不允许新下载或新关联。
- `DELETE_FAILED`：对象删除失败，允许管理员重试。
- `DELETED`：对象删除完成，数据库记录逻辑删除。

文件内容只保存在 MinIO，数据库保存元数据和对象定位信息：

```text
bucket_name + object_key
```

`object_key` 由服务端生成，不使用用户输入直接拼接：

```text
users/{userId}/{yyyy}/{MM}/{dd}/{uuid}.{extension}
```

例如：

```text
users/100/2026/10/27/550e8400-e29b-41d4-a716-446655440000.pdf
```

原始文件名只用于展示和下载时的 `Content-Disposition`，不能作为对象路径的一部分。

### 5.2 聊天消息文件关联表

```sql
create table if not exists chat_message_file (
    id bigint not null auto_increment primary key comment '关联主键 ID',
    message_id bigint not null comment '聊天消息 ID',
    file_id bigint not null comment '文件 ID',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_message_file (message_id, file_id),
    key idx_message_file_file (file_id),
    key idx_message_file_message (message_id)
) comment = '聊天消息文件关联表';
```

表结构沿用当前项目不显式创建外键的做法，由服务层校验消息和文件归属。

## 6. 存储服务设计

### 6.1 对象存储接口

```java
public interface ObjectStorage {

    void put(String bucketName,
             String objectKey,
             InputStream inputStream,
             long contentLength,
             String contentType);

    InputStream get(String bucketName, String objectKey);

    void delete(String bucketName, String objectKey);

    String createPresignedGetUrl(String bucketName,
                                  String objectKey,
                                  Duration expire);
}
```

### 6.2 文件业务接口

```java
public interface FileService {

    FileMetadataVO upload(MultipartFile file);

    FileMetadataVO findAccessible(Long fileId);

    String createDownloadUrl(Long fileId);

    InputStream download(Long fileId);

    void deleteOwned(Long fileId);

    PageResult<FileMetadataVO> page(FileQueryRequest request);

    void deleteByAdmin(Long fileId);

    List<FileMetadataVO> validateOwnedActiveFiles(
            Long userId,
            List<Long> fileIds);
}
```

`validateOwnedActiveFiles` 是聊天模块使用的边界接口，只返回当前用户拥有且状态为 `ACTIVE` 的文件，不暴露 MinIO 实现细节。

## 7. HTTP 接口设计

### 7.1 普通用户接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/files` | 上传单个文件 |
| `GET` | `/api/files/{fileId}` | 查询文件元数据 |
| `GET` | `/api/files/{fileId}/download` | 下载文件，后端代理文件流 |
| `GET` | `/api/files/{fileId}/presigned-url` | 获取临时下载 URL |
| `DELETE` | `/api/files/{fileId}` | 删除自己的文件 |

上传使用 `multipart/form-data`，字段名为 `file`。单次请求上传一个文件。

上传响应：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "id": 1001,
    "originalName": "report.pdf",
    "contentType": "application/pdf",
    "fileSize": 524288,
    "status": "ACTIVE",
    "createdAt": "2026-10-27T15:30:00"
  }
}
```

`download` 接口在完成权限校验后从 MinIO 读取流，并设置正确的 `Content-Type` 和 UTF-8 文件名。预签名接口返回短期 URL：

```json
{
  "success": true,
  "code": "SUCCESS",
  "data": {
    "fileId": 1001,
    "url": "https://minio.example.com/temporary-url",
    "expiresIn": 3600
  }
}
```

系统不保存预签名 URL。MinIO endpoint、bucket 或访问策略变化时，只需要重新生成 URL。

### 7.2 管理员接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| `GET` | `/api/admin/files` | `file:read` | 分页查询文件 |
| `GET` | `/api/admin/files/{fileId}` | `file:read` | 查询文件详情 |
| `DELETE` | `/api/admin/files/{fileId}` | `file:delete` | 删除任意用户文件 |

管理员查询参数：

```text
page
size
userId
originalName
contentType
status
createdFrom
createdTo
```

在权限数据中增加 `file:read` 和 `file:delete`，具体授权沿用当前 Sa-Token RBAC 模型。

## 8. 文件校验和权限

上传时校验：

- 文件不能为空。
- 文件大小不能超过 `file.storage.max-file-size`。
- MIME 类型必须在允许列表中。
- 文件名长度不能超过 255 个字符。
- object key 必须完全由服务端生成。
- 对图片、PDF、Office 等二进制类型校验文件签名，不能只信任客户端传入的 MIME 类型。
- 不允许客户端指定 bucket、object key 或永久访问 URL。

访问权限：

- 普通用户只能访问、下载和删除自己上传的文件。
- 聊天消息关联文件时，必须校验文件属于当前用户且状态为 `ACTIVE`。
- 管理员通过 `file:read`、`file:delete` 权限访问管理接口。
- 文件通过聊天消息展示时，仍然根据消息所属会话用户校验，不因知道 `fileId` 就获得访问权限。
- bucket 保持私有，预签名 URL 默认有效期为 1 小时。

错误码：

```text
FILE_EMPTY
FILE_TOO_LARGE
FILE_TYPE_NOT_ALLOWED
FILE_NOT_FOUND
FILE_ACCESS_DENIED
FILE_STORAGE_ERROR
FILE_DELETE_FAILED
FILE_COUNT_EXCEEDED
```

错误响应继续使用当前项目的 `ApiResponse` 和全局异常处理机制：参数错误返回 400，资源不存在返回 404，无权限返回 403，MinIO 不可用返回 503。

## 9. 上传、删除一致性

### 9.1 上传

```text
校验 multipart 文件
  -> 生成 object key
  -> 上传 MinIO
  -> 插入 file_metadata
  -> 返回文件信息
```

如果数据库插入失败，尝试删除已上传的 MinIO 对象，避免产生孤儿对象。如果补偿删除也失败，记录 object key 和错误日志，供管理员清理。

### 9.2 删除

```text
校验文件权限
  -> file_metadata.status = DELETING
  -> 删除 MinIO 对象
  -> file_metadata.status = DELETED、deleted = 1
```

如果 MinIO 删除失败，设置 `DELETE_FAILED`，不逻辑删除数据库记录，管理员可以重试。若服务在 MinIO 删除成功后、数据库更新前崩溃，记录会停留在 `DELETING`，重试删除时使用 MinIO 的幂等删除行为，最终将记录标记为 `DELETED`。

数据库事务不能覆盖 MinIO 操作，因此不能使用一个本地事务假设两者自动回滚。

## 10. 聊天模块接入

修改现有 `ChatRequestDTO`：

```java
@Size(max = 10)
List<Long> fileIds
```

请求示例：

```json
{
  "content": "请帮我查看这些文件",
  "fileIds": [1001, 1002]
}
```

当前聊天请求仍要求 `content` 非空，附件-only 消息不属于本阶段范围。

流式聊天创建用户消息前：

1. 校验会话属于当前用户。
2. 调用 `FileService.validateOwnedActiveFiles` 校验所有 `fileIds`。
3. 创建用户消息。
4. 批量写入 `chat_message_file`。
5. 创建助手消息并开始流式生成。

新增 `ChatMessageFile`、`ChatMessageFileMapper` 和对应服务。消息查询 VO 增加附件列表：

```java
List<FileMetadataVO> files
```

重新生成助手消息时，复用原用户消息已有的附件关联，不通过重新生成请求更换附件。文件内容本阶段只用于附件管理和下载，不进入 `ChatMessagePair` 或 AI 上下文。

## 11. 测试设计

### 11.1 文件模块单元测试

- 空文件、超大文件和不允许类型被拒绝。
- object key 不包含未经处理的原始文件名。
- 上传成功后正确保存元数据。
- 数据库保存失败时调用对象清理。
- 非所有者无法查询、下载或删除文件。
- 已删除、删除中和删除失败的文件不能生成下载 URL。
- 管理员可以查询任意用户文件。
- 管理员删除失败后可以重试。

### 11.2 聊天模块测试

- 用户可以关联自己的有效文件。
- 用户不能关联其他用户的文件。
- 不存在或已删除的文件 ID 被拒绝。
- 相同文件不能重复关联同一消息。
- 消息查询返回附件元数据。
- 重新生成保留原用户消息的附件。

### 11.3 集成测试

- 使用 MinIO 测试实例验证真实上传、读取、删除和预签名 URL。
- 验证私有 bucket 未认证时不能直接读取对象。
- 验证 MySQL 迁移能创建文件表和聊天关联表。
- 验证文件模块和聊天模块组合启动后 Mapper、权限和接口正常工作。

## 12. 实施顺序

1. 在父 POM 和 `ai-platform-boot` 中加入 `ai-platform-file` 模块。
2. 增加 MinIO 依赖、配置属性和 `MinioClient` 装配。
3. 实现 `ObjectStorage` 和 `MinioObjectStorage`。
4. 增加文件表迁移、实体、Mapper 和状态枚举。
5. 实现文件上传、元数据查询、下载、预签名 URL 和用户删除。
6. 增加文件类型、大小、归属和状态校验。
7. 增加管理员文件查询和删除接口及权限。
8. 在 `ai-platform-chat` 中增加消息文件关联表和服务。
9. 扩展 `ChatRequestDTO`、聊天编排流程和消息 VO。
10. 编写单元测试和 MinIO 集成测试。
11. 更新后端开发文档和前端接口文档。

## 13. 验收标准

- 业务模块中不存在直接调用 MinIO SDK 的代码。
- MinIO bucket 默认是私有的。
- 数据库保存文件元数据和 object key，不把永久 URL 作为文件唯一标识。
- 所有文件访问经过所有权或管理员权限校验。
- 上传和删除失败时有明确状态和错误响应。
- 文件大小和类型由后端强制校验。
- 聊天消息只保存 `file_id` 关联，不保存 MinIO 地址。
- 文件删除后不能生成新的预签名 URL。
- 管理员可以分页查询和删除文件。
- 替换 MinIO 时只需要新增 `ObjectStorage` 实现，不修改文件业务和聊天业务。
