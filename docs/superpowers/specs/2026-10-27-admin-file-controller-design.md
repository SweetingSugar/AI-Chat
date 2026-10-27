# AdminFileController 模块归属调整设计

## 目标

将文件管理端接口从 `ai-platform-file` 移至 `ai-platform-admin`，使管理端 HTTP 接口统一归属 admin 模块，同时保持现有接口行为不变。

## 方案

采用整体迁移 Controller 层的方式：

- 将 `AdminFileController` 移至 `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/`，包名改为 `com.example.aichat.admin.controller`。
- 将 `AdminFileControllerTest` 一并移至 admin 模块对应测试目录，并更新包声明及导入。
- 为 `ai-platform-admin` 增加 `ai-platform-file` 编译依赖，以使用 `FileService`、`FileQueryRequest` 和 `FileMetadataVO`。
- 从 `ai-platform-file` 删除原控制器及测试，保留普通文件接口、文件领域服务、DTO/VO、权限迁移脚本和数据库能力。

## 接口兼容性

以下内容保持不变：

- `GET /api/admin/files`
- `GET /api/admin/files/{fileId}`
- `DELETE /api/admin/files/{fileId}`
- `file:read` 和 `file:delete` 权限标识
- `FileService` 调用方式与响应结构

由于 Spring Boot 启动模块会同时加载 admin 和 file 模块，控制器迁移不会改变运行时路由。

## 依赖与边界

`ai-platform-admin -> ai-platform-file` 作为单向依赖。文件服务仍留在 file 模块，chat 对文件能力的依赖保持不变；file 模块不依赖 admin，因此不会产生循环依赖。

## 测试与验证

- 在 admin 模块运行 `AdminFileControllerTest`，验证列表接口的 HTTP 状态、成功标识和文件记录返回值。
- 运行 Maven 多模块测试或至少编译 admin 及其依赖，确认依赖调整、包迁移和 Spring Controller 注册均无编译问题。
- 检查 file 模块不再包含 `AdminFileController`，且管理接口路径和权限引用只保留一份。
