# MinIO File Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有多模块 Spring Boot 项目中新增 `ai-platform-file`，实现基于 MinIO 的文件上传、下载、删除、元数据持久化、权限校验、预签名 URL、管理员管理和聊天消息附件关联。

**Architecture:** 新模块只通过 `ObjectStorage` 接口访问对象存储，`MinioObjectStorage` 是唯一的 MinIO SDK 适配器；文件元数据保存在 MySQL，聊天模块只依赖 `FileAccessService` 并保存 `chat_message_file` 关联。普通用户按文件上传者校验权限，管理员通过 Sa-Token 权限码访问管理接口。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring MVC、MyBatis-Plus、Flyway、Sa-Token、MySQL、MinIO Java SDK、JUnit 5、Mockito。

---

## 文件变更总览

### 新增

- `ai-platform-file/pom.xml`：文件模块依赖。
- `ai-platform-file/src/main/java/com/example/aichat/file/config/FileStorageProperties.java`：文件和 MinIO 配置属性。
- `ai-platform-file/src/main/java/com/example/aichat/file/config/MinioConfiguration.java`：MinIO 客户端 Bean。
- `ai-platform-file/src/main/java/com/example/aichat/file/storage/ObjectStorage.java`：对象存储 SPI。
- `ai-platform-file/src/main/java/com/example/aichat/file/storage/MinioObjectStorage.java`：MinIO SPI 实现。
- `ai-platform-file/src/main/java/com/example/aichat/file/entity/FileMetadata.java`：文件元数据实体。
- `ai-platform-file/src/main/java/com/example/aichat/file/enums/FileStatus.java`：文件状态枚举。
- `ai-platform-file/src/main/java/com/example/aichat/file/mapper/FileMetadataMapper.java`：文件 Mapper。
- `ai-platform-file/src/main/java/com/example/aichat/file/dto/FileQueryRequest.java`：管理员查询参数。
- `ai-platform-file/src/main/java/com/example/aichat/file/vo/FileMetadataVO.java`：文件响应对象。
- `ai-platform-file/src/main/java/com/example/aichat/file/vo/FileDownloadUrlVO.java`：预签名 URL 响应对象。
- `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java`：文件业务接口。
- `ai-platform-file/src/main/java/com/example/aichat/file/service/FileAccessService.java`：聊天模块使用的文件访问接口。
- `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java`：文件业务实现。
- `ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java`：普通文件接口。
- `ai-platform-file/src/main/java/com/example/aichat/file/controller/AdminFileController.java`：管理员文件接口。
- `ai-platform-file/src/main/java/com/example/aichat/file/exception/FileErrorCode.java`：文件错误码到通用错误码的映射。
- `ai-platform-file/src/main/resources/db/migration/V7__file_metadata_schema.sql`：文件元数据表。
- `ai-platform-file/src/main/resources/db/migration/V9__file_permission.sql`：文件管理权限数据。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/entity/ChatMessageFile.java`：聊天消息文件关联实体。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/mapper/ChatMessageFileMapper.java`：关联 Mapper。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/ChatMessageFileService.java`：关联服务。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageFileServiceImpl.java`：关联服务实现。
- `ai-platform-chat/src/main/resources/db/migration/V8__chat_message_file_schema.sql`：聊天消息文件关联表。
- `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java`：文件服务测试。
- `ai-platform-file/src/test/java/com/example/aichat/file/storage/MinioObjectStorageTest.java`：存储适配器测试。
- `ai-platform-chat/src/test/java/com/example/aichat/chat/service/ChatMessageFileServiceTest.java`：聊天附件关联测试。

### 修改

- `pom.xml`：加入 `ai-platform-file` Maven module。
- `ai-platform-boot/pom.xml`：加入文件模块运行时依赖。
- `ai-platform-chat/pom.xml`：加入文件模块依赖。
- `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`：加入文件 Mapper 扫描包。
- `ai-platform-boot/src/main/resources/application.yml`：加入 MinIO、文件大小和 multipart 配置。
- `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`：补充文件存储错误的 HTTP 映射。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRequestDTO.java`：增加最多 10 个 `fileIds`。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java`：携带用户消息附件 ID。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java`：创建生成命令前校验附件归属。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatGenerationServiceImpl.java`：用户消息落库后绑定附件。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageServiceImpl.java`：查询消息时批量组装附件。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/vo/ChatMessageVO.java`：增加文件列表。
- `ai-platform-chat/src/main/java/com/example/aichat/chat/converter/ChatMessageConverter.java`：转换附件列表。
- `ai-platform-chat/src/main/resources/mapper/ChatMessageMapper.xml`：保持现有历史查询，附件由关联服务批量加载。

---

## Task 1: 建立模块和运行配置

**Files:**
- Modify: `pom.xml`
- Create: `ai-platform-file/pom.xml`
- Modify: `ai-platform-boot/pom.xml`
- Modify: `ai-platform-chat/pom.xml`
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`
- Modify: `ai-platform-boot/src/main/resources/application.yml`

- [ ] **Step 1: 在父 POM 注册模块**

在 `<modules>` 中加入：

```xml
<module>ai-platform-file</module>
```

放在 `ai-platform-common` 之后，确保文件模块先于聊天模块参与 Maven reactor 构建。

- [ ] **Step 2: 创建文件模块 POM**

`ai-platform-file/pom.xml` 使用现有父 POM，并声明：

```xml
<dependencies>
    <dependency>
        <groupId>com.example.aichat</groupId>
        <artifactId>ai-platform-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>io.minio</groupId>
        <artifactId>minio</artifactId>
        <version>${minio.version}</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

在父 POM 增加 `<minio.version>8.6.0</minio.version>` 属性，并在 `dependencyManagement` 中统一管理 MinIO 版本；文件模块额外显式引入 `com.squareup.okhttp3:okhttp-jvm:5.3.2`，因为 MinIO 8.6.0 的 Maven 元数据未将 OkHttp JVM 平台包作为直接依赖；业务代码不单独声明 MinIO 版本。

- [ ] **Step 3: 连接模块依赖**

在 `ai-platform-boot/pom.xml` 增加 `ai-platform-file` 依赖；在 `ai-platform-chat/pom.xml` 增加同一依赖。不要让 `ai-platform-file` 依赖 `ai-platform-chat`，避免循环依赖。

- [ ] **Step 4: 注册 Mapper 扫描**

将启动类的 `@MapperScan` 修改为包含：

```java
"com.example.aichat.file.mapper"
```

- [ ] **Step 5: 增加应用配置**

在 `application.yml` 增加：

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

spring:
  servlet:
    multipart:
      max-file-size: ${FILE_MAX_SIZE:10MB}
      max-request-size: ${FILE_MAX_REQUEST_SIZE:10MB}
```

- [ ] **Step 6: 构建模块骨架**

运行：

```bash
mvn -pl ai-platform-file -am test -DskipTests
```

预期：构建成功，尚未包含文件业务类。

---

## Task 2: 实现 MinIO 配置和对象存储适配器

**Files:**
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/config/FileStorageProperties.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/config/MinioConfiguration.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/storage/ObjectStorage.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/storage/MinioObjectStorage.java`
- Create: `ai-platform-file/src/test/java/com/example/aichat/file/storage/MinioObjectStorageTest.java`

- [ ] **Step 1: 写存储适配器测试**

使用 Mockito mock `MinioClient`，验证 `ObjectStorage` 的四个操作分别调用对应的 MinIO API：`putObject`、`getObject`、`removeObject` 和 `getPresignedObjectUrl`。测试还要验证上传请求携带对象长度和 content type，预签名请求使用配置的 GET 方法和有效期。

- [ ] **Step 2: 实现配置属性**

使用 `@ConfigurationProperties(prefix = "file.storage")` 定义：

```java
public class FileStorageProperties {
    private String type;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean secure;
    private long presignExpireSeconds;
    private long maxFileSize;
    private Set<String> allowedContentTypes;
}
```

为所有字段提供 getter/setter。`allowedContentTypes` 使用不区分大小写的集合比较。

- [ ] **Step 3: 创建 MinioClient Bean**

`MinioConfiguration` 使用 endpoint、access key、secret key 和 secure 构造 `MinioClient`，并注册 `FileStorageProperties`。配置类不向业务层暴露 MinIO 客户端以外的实现细节。

- [ ] **Step 4: 实现 ObjectStorage**

定义：

```java
public interface ObjectStorage {
    void put(String bucketName, String objectKey, InputStream inputStream,
             long contentLength, String contentType);
    InputStream get(String bucketName, String objectKey);
    void delete(String bucketName, String objectKey);
    String createPresignedGetUrl(String bucketName, String objectKey, Duration expire);
}
```

`MinioObjectStorage` 将 MinIO 异常转换为 `BusinessException`，不把 MinIO SDK 异常传到 Controller。上传前确保 bucket 存在；删除使用 MinIO 的幂等删除行为。不要将 object key 或 access key 写入日志。

- [ ] **Step 5: 运行适配器测试**

运行：

```bash
mvn -pl ai-platform-file -Dtest=MinioObjectStorageTest test
```

预期：全部通过。

---

## Task 3: 实现文件元数据、迁移和权限错误码

**Files:**
- Create: `ai-platform-file/src/main/resources/db/migration/V7__file_metadata_schema.sql`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/entity/FileMetadata.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/enums/FileStatus.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/mapper/FileMetadataMapper.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/exception/FileErrorCode.java`
- Create: `ai-platform-file/src/main/resources/db/migration/V9__file_permission.sql`
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`

- [ ] **Step 1: 写 V7 文件表迁移**

使用已确认的 `file_metadata` DDL：字段包括 `user_id`、`bucket_name`、`object_key`、`original_name`、`content_type`、`file_size`、`file_hash`、`status`、时间字段和逻辑删除字段；增加 `(bucket_name, object_key)` 唯一索引及用户、状态查询索引。

- [ ] **Step 2: 写实体和状态枚举**

`FileMetadata` 使用：

```java
@TableName("file_metadata")
public class FileMetadata {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String bucketName;
    public String objectKey;
    public String originalName;
    public String contentType;
    public Long fileSize;
    public String fileHash;
    public FileStatus status;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
```

`FileStatus` 定义 `ACTIVE`、`DELETING`、`DELETE_FAILED`、`DELETED`。

- [ ] **Step 3: 创建 Mapper**

`FileMetadataMapper` 继承 `BaseMapper<FileMetadata>` 并使用 `@Mapper`。分页查询使用 MyBatis-Plus wrapper，单文件访问使用 `id`、`user_id` 和逻辑删除条件。

- [ ] **Step 4: 增加错误码**

在通用 `ErrorCode` 中增加文件存储不可用和删除失败的 503 错误码；在 `FileErrorCode` 中将业务错误映射到 `VALIDATION_ERROR`、`RESOURCE_NOT_FOUND`、`FORBIDDEN` 或文件存储错误。所有文件服务异常继续通过 `BusinessException` 进入现有全局异常处理器。

- [ ] **Step 5: 写文件权限数据迁移**

`V9__file_permission.sql` 使用现有权限数据风格：

```sql
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'File read', 'file:read', 3, 200, 1),
    (0, 'File delete', 'file:delete', 3, 201, 1);
```

不自动修改角色权限；权限数据创建后由现有角色管理接口分配给管理员角色。

- [ ] **Step 6: 编译并验证迁移资源**

运行：

```bash
mvn -pl ai-platform-file -am test -DskipTests
```

预期：实体、Mapper 和迁移资源编译通过。

---

## Task 4: 实现文件业务服务和普通用户接口

**Files:**
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/dto/FileQueryRequest.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/vo/FileMetadataVO.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/vo/FileDownloadUrlVO.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileAccessService.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java`
- Create: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java`

- [ ] **Step 1: 写文件服务失败测试**

在 `FileServiceImplTest` 中 mock `FileMetadataMapper`、`ObjectStorage`、`CurrentUserProvider`，先覆盖：

```java
@Test
void rejectsDisallowedContentType()

@Test
void rejectsFileLargerThanConfiguredLimit()

@Test
void rejectsAccessToAnotherUsersFile()

@Test
void removesObjectWhenMetadataInsertFails()

@Test
void createsPresignedUrlOnlyForActiveOwnedFile()
```

测试用户固定为 `CurrentUser(100L, "alice")`，另一个用户使用 `200L`，文件 ID 使用 `1001L`。

- [ ] **Step 2: 实现文件名和 object key 生成**

使用当前用户 ID、UTC 或项目统一时区日期和 UUID 生成 key；扩展名只从经过规范化的原始文件名提取，不能包含 `/`、`\\` 或 `..`。原始文件名仅保存到 `originalName`。

- [ ] **Step 3: 实现上传**

上传服务执行：

1. `CurrentUserProvider.requireCurrentUser()`。
2. 校验文件不为空、大小不超过配置、content type 在白名单。
3. 生成 bucket 和 object key。
4. 调用 `ObjectStorage.put`。
5. 插入 `FileMetadata`，状态为 `ACTIVE`、`deleted` 为 0。
6. 插入失败时调用 `ObjectStorage.delete` 补偿。
7. 返回不包含 bucket、object key 和凭证的 `FileMetadataVO`。

可以在上传流读取过程中计算 SHA-256；如果实现选择暂不计算，`file_hash` 保存为空，不能用客户端传入的 hash 作为可信值。

- [ ] **Step 4: 实现用户查询、下载和预签名 URL**

文件查询必须同时检查 `id`、当前用户 `userId`、`status = ACTIVE` 和逻辑删除。`download` 从 `ObjectStorage.get` 返回文件流，Controller 设置服务端保存的 content type 和经过 RFC 5987 编码的原始文件名。预签名 URL 使用配置的有效期，不把 URL 写入数据库。

- [ ] **Step 5: 实现用户删除**

删除服务将记录从 `ACTIVE` 更新为 `DELETING`，调用 `ObjectStorage.delete`，成功后更新为 `DELETED` 并设置逻辑删除；MinIO 异常时更新为 `DELETE_FAILED`，保持 `deleted = 0` 并返回删除失败错误。

- [ ] **Step 6: 实现普通 Controller**

接口：

```text
POST   /api/files
GET    /api/files/{fileId}
GET    /api/files/{fileId}/download
GET    /api/files/{fileId}/presigned-url
DELETE /api/files/{fileId}
```

上传参数名为 `file`，下载接口返回二进制流，其他接口返回现有 `ApiResponse`。Controller 不直接注入 `MinioClient`。

- [ ] **Step 7: 运行服务测试**

运行：

```bash
mvn -pl ai-platform-file -Dtest=FileServiceImplTest test
```

预期：全部通过，且失败上传不会留下未清理的对象调用缺失。

---

## Task 5: 实现管理员文件查询和删除

**Files:**
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/controller/AdminFileController.java`
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java`
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java`

- [ ] **Step 1: 增加管理员分页查询**

`FileQueryRequest` 使用当前项目分页约束，并支持 `userId`、`originalName`、`contentType`、`status`、`createdFrom`、`createdTo`。查询只返回未逻辑删除记录，按 `create_time desc, id desc` 排序。

- [ ] **Step 2: 增加管理员 Controller**

实现：

```text
GET    /api/admin/files
GET    /api/admin/files/{fileId}
DELETE /api/admin/files/{fileId}
```

分别使用：

```java
@SaCheckPermission("file:read")
@SaCheckPermission("file:delete")
```

管理员删除复用普通删除状态机，但跳过用户归属校验。

- [ ] **Step 3: 验证权限和查询**

通过 Controller 测试或现有权限测试模式验证：没有 `file:read` 的用户不能访问列表，没有 `file:delete` 的用户不能删除文件；管理员查询可以过滤任意用户。

---

## Task 6: 接入聊天消息附件关联

**Files:**
- Create: `ai-platform-chat/src/main/resources/db/migration/V8__chat_message_file_schema.sql`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/entity/ChatMessageFile.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/mapper/ChatMessageFileMapper.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/ChatMessageFileService.java`
- Create: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageFileServiceImpl.java`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/service/ChatMessageFileServiceTest.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRequestDTO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatGenerationServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatGenerationServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/vo/ChatMessageVO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/converter/ChatMessageConverter.java`

- [ ] **Step 1: 写 V8 关联表迁移**

使用 `chat_message_file` 表，字段为 `message_id`、`file_id`、`sort_order`、`create_time`，增加 `(message_id, file_id)` 唯一索引和消息、文件查询索引。

- [ ] **Step 2: 增加关联实体和 Mapper**

`ChatMessageFile` 使用 `@TableName("chat_message_file")` 和自增主键；Mapper 继承 `BaseMapper<ChatMessageFile>`，提供按消息 ID 批量查询的方法。`ChatMessageFileService` 提供 `bind(Long messageId, Long userId, List<Long> fileIds)` 和按消息 ID 批量组装附件的方法，组装时先查询关联记录，再按 file ID 批量调用 `FileAccessService.findByFileIds`，不能逐条查询文件。

- [ ] **Step 3: 写关联服务失败测试**

使用 mock `FileAccessService` 和 `ChatMessageFileMapper` 验证：

```java
@Test
void bindsOnlyFilesOwnedByCurrentUser()

@Test
void rejectsMissingOrInactiveFile()

@Test
void preservesRequestedFileOrder()
```

- [ ] **Step 4: 实现文件访问接口**

在文件模块实现：

```java
public interface FileAccessService {
    List<FileMetadataVO> validateOwnedActiveFiles(Long userId, List<Long> fileIds);
    List<FileMetadataVO> findByFileIds(Long userId, List<Long> fileIds);
}
```

批量校验必须保证返回数量等于去重后的请求 ID 数量；任何 ID 不存在、非当前用户或非 `ACTIVE` 都抛出业务异常。

- [ ] **Step 5: 扩展聊天请求和保存流程**

`ChatRequestDTO` 增加：

```java
@Size(max = 10)
List<Long> fileIds
```

在 `ChatServiceImpl.stream` 中，在构造 `ChatGenerationCommand` 前调用 `FileAccessService.validateOwnedActiveFiles`，并将去重后的 `fileIds` 放入 command。实际用户消息是在 `ChatGenerationServiceImpl.start` 中保存的；正常生成在 `saveUser` 成功后调用 `ChatMessageFileService.bind` 批量插入 `chat_message_file`，该方法与生成任务创建处于同一个事务。文件关联失败时回滚消息和任务，不启动 AI 生成。

重新生成流程不接受新的 `fileIds`，复用原用户消息已存在的关联。

- [ ] **Step 6: 扩展消息 VO 和历史查询**

`ChatMessageVO` 增加 `List<FileMetadataVO> files`。`ChatMessageServiceImpl.page` 和 `listAll` 在取得消息列表后批量按消息 ID加载附件，避免逐条查询。`ChatMessageConverter` 接收附件列表并创建 VO；没有附件时返回空列表而不是 null。

- [ ] **Step 7: 运行聊天附件测试**

运行：

```bash
mvn -pl ai-platform-chat -am -Dtest=ChatMessageFileServiceTest test
```

预期：附件归属、状态、顺序和重复关联测试全部通过。

---

## Task 7: 集成验证和文档更新

**Files:**
- Modify: `docs/backend-development-guide.md`
- Modify: `docs/frontend-api-integration.md`
- Test: `ai-platform-file/src/test/java/com/example/aichat/file/storage/MinioObjectStorageTest.java`

- [ ] **Step 1: 增加 MinIO 集成验证**

使用测试 MinIO 实例验证真实的上传、读取、删除和预签名 URL；验证 bucket 为私有，未授权请求不能直接读取对象。若 CI 没有容器运行环境，保留适配器单元测试作为默认构建测试，并通过 profile 执行 MinIO 集成测试。

- [ ] **Step 2: 更新后端开发文档**

在模块划分、数据表、配置和接口章节加入 `ai-platform-file`、`file_metadata`、`chat_message_file`、MinIO 环境变量及管理员权限说明。

- [ ] **Step 3: 更新前端接口文档**

加入 multipart 上传示例、文件 ID 使用方式、预签名 URL 获取方式、下载和删除接口，以及聊天请求中的 `fileIds`。

- [ ] **Step 4: 执行全量验证**

运行：

```bash
mvn clean verify
```

预期：所有模块测试通过，Flyway 迁移资源可加载，项目可以打包生成可执行 Boot JAR。

- [ ] **Step 5: 检查实现边界**

使用以下命令确认 MinIO SDK 没有泄漏到业务模块：

```bash
grep -RIn "io.minio\|MinioClient" --include='*.java' ai-platform-* \
  | grep -v 'ai-platform-file/src/main/java/com/example/aichat/file/'
```

预期：无输出。确认聊天模块只依赖 `FileAccessService`，不包含 bucket、object key 或 MinIO 客户端调用。

## 验收标准

- `ai-platform-file` 能独立编译并由 `ai-platform-boot` 启动。
- 文件对象只保存于 MinIO，MySQL 只保存元数据和 object key。
- 文件上传、下载、预签名 URL 和删除均经过用户归属或管理员权限校验。
- 上传失败时会清理已经写入的对象。
- 删除失败时保留 `DELETE_FAILED` 状态并可重试。
- 文件类型和大小由服务端强制校验。
- 聊天消息可以关联最多 10 个当前用户的有效文件。
- 聊天消息历史返回附件元数据且不逐条查询数据库。
- 管理员可以分页查询和删除任意用户文件。
- MinIO SDK 只出现在 `ai-platform-file` 模块。
