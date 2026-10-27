# 管理文件查询日期参数格式调整设计

## 目标

将管理文件接口的 `createdFrom` 和 `createdTo` 请求参数统一改为 `yyyy-MM-dd`，并按完整自然日执行查询。

## 方案

- `FileQueryRequest.createdFrom` 和 `createdTo` 从 `LocalDateTime` 改为 `LocalDate`。
- 使用 `@DateTimeFormat(pattern = "yyyy-MM-dd")` 绑定日期参数。
- `FileServiceImpl.page` 查询时：
  - `createdFrom` 转换为当天 `00:00:00`，使用 `>=`。
  - `createdTo` 转换为次日 `00:00:00`，使用 `<`，确保包含结束日全部数据。
- Controller 路径、其他查询参数、权限校验和响应结构保持不变。

## 测试

- Controller 测试传入 `createdFrom=2026-10-01`、`createdTo=2026-10-27`，验证 Spring 能正确绑定日期参数并调用服务。
- FileService 测试验证起止日期转换为整日边界，且结束时间使用次日零点。
- 运行 file 与 admin 相关测试，随后执行 admin 及其依赖模块测试。
