# 知识库 PostgreSQL 数据访问迁移至 MyBatis XML

- 日期：2026-11-02
- 状态：已获设计确认，待实施计划
- 范围：将知识库模块的 PostgreSQL 业务数据访问从 JdbcTemplate/PreparedStatement 迁移到独立 MyBatis Mapper XML

## 1. 目标与边界

知识库模块的生产代码不再直接使用 `JdbcTemplate` 或 `PreparedStatement` 执行 PostgreSQL 业务 SQL。5 个 Repository 保留现有对 Service 层的公共方法和领域职责，内部改为调用 PostgreSQL 专用 MyBatis Mapper。

Flyway 迁移脚本仍然保留在 `db/migration/knowledge-postgresql`；迁移脚本和测试数据库初始化不属于业务数据访问，不迁移为 MyBatis XML。MySQL 的现有 MyBatis-Plus 配置和 Mapper 不做改造。

## 2. 数据源与 MyBatis 架构

在 `ai-platform-boot` 增加 PostgreSQL 专用 MyBatis 配置，创建：

- `postgresqlSqlSessionFactory`，数据源为 `postgresqlDataSource`；
- `postgresqlSqlSessionTemplate`；
- `postgresqlTransactionManager`；
- 仅扫描 `com.example.aichat.knowledge.mapper` 的 `@MapperScan`。

PostgreSQL 会话工厂只加载 `classpath*:mapper/postgresql/**/*.xml`。知识库 Mapper 不加入现有默认 MySQL `@MapperScan`，避免 PostgreSQL SQL 使用错误的数据源。现有 MySQL 会话工厂、Mapper 扫描和配置保持不变。

知识库模块增加 MyBatis-Plus/MyBatis 运行时依赖，使 Mapper 接口可以在模块内编译；Repository 不再依赖 `JdbcTemplate`。

## 3. Mapper 与 XML 设计

新增以下 Mapper 接口及 XML：

- `KnowledgeBaseMapper`
- `KnowledgeDocumentMapper`
- `KnowledgeChunkMapper`
- `KnowledgeChunkEmbeddingMapper`
- `KnowledgeIndexTaskMapper`

XML 统一放在 `ai-platform-knowledge/src/main/resources/mapper/postgresql/`，每个 XML 的 namespace 对应一个 Mapper 接口。Repository 继续负责：

- 将 Service 参数传给 Mapper；
- 将不可变 `record` 和 Mapper 返回值组装成领域记录；
- 使用 `ObjectMapper` 在 `Map<String, Object>` 与 JSON 字符串之间转换。

所有查询、插入、更新、删除、分页、批量写入、向量检索和任务领取 SQL 均放在 XML，Java 中不保留 PostgreSQL 业务 SQL 字符串。

## 4. 结果映射与 PostgreSQL 类型

对 `KnowledgeBaseRecord`、`KnowledgeDocumentRecord`、`KnowledgeChunkRecord` 和 `KnowledgeIndexTaskRecord` 定义显式 `resultMap`，使用 `<constructor>` 按 Java record 规范构造器的参数顺序映射字段，显式处理 snake_case 列名、枚举、可空字段和 `LocalDateTime`。

新增 `PgVectorTypeHandler`：

- 将 `float[]` 参数序列化为 `[1.0,2.0]`；
- 通过 `PreparedStatement#setString` 绑定参数；
- XML 使用 `cast(#{vector, typeHandler=...} as vector)`；
- 不允许将向量文本拼接进 SQL。

chunk 元数据由 Repository 序列化为 JSON 字符串，XML 通过 `cast(#{metadataJson} as jsonb)` 写入。读取后继续由 Repository 使用 `ObjectMapper` 还原。

插入主键使用 PostgreSQL `insert ... returning id`。对应 XML 使用返回结果的查询映射，Repository 根据返回的 ID 创建新的 record，不再使用 `GeneratedKeyHolder`。

## 5. 关键 SQL 行为

- 知识库：按用户分页、计数、详情、归属校验、插入、更新、换模型、版本切换和逻辑删除；
- 文档：按知识库分页、计数、详情、文件关联校验、插入、状态更新、内容 hash 更新和逻辑删除；
- chunk：按文档/索引版本查询、插入、删除；
- embedding：批量插入、按文档和版本删除、按知识库/模型/维度/版本进行 pgvector 相似度搜索；
- 索引任务：插入、`claimNext`、成功/失败、重试、超时恢复、计数和详情查询。

`claimNext` 保留单条 `with candidate ... for update skip locked ... update ... returning` 语句，确保并发 Worker 不重复领取任务。相似度查询保留模型 ID、维度、索引版本和文档状态过滤，并使用绑定参数传递查询向量。

配置 PostgreSQL 专用事务管理器，但本次迁移不改变现有 Service 的调用流程和异常处理，不引入与本需求无关的业务重构。

## 6. 测试与验收

更新 PostgreSQL Repository 集成测试，使其通过 PostgreSQL MyBatis 配置获取 Mapper/Repository，并继续使用 pgvector Testcontainer 验证：

- 5 类 Repository 的查询和写入；
- `insert ... returning id` 的主键返回；
- JSONB 和 vector 参数映射；
- 相似度查询的模型、维度、版本隔离；
- `claimNext` 的 `skip locked` 行为；
- 分页、逻辑删除、失败重试和任务恢复。

新增配置测试，验证 PostgreSQL 会话工厂使用 `postgresqlDataSource`，XML 能被加载，Mapper 不进入 MySQL 会话工厂，专用模板和事务管理器正确创建。

继续运行知识库、Boot、AI、聊天模块测试，并通过静态检查确认 `ai-platform-knowledge/src/main/java` 不再引用 `JdbcTemplate`、`PreparedStatement` 或内嵌 PostgreSQL 业务 SQL。Flyway 脚本和测试数据库初始化代码不计入该限制。

验收标准：知识库生产代码中的 PostgreSQL 业务操作全部由 PostgreSQL 专用 MyBatis Mapper XML 执行，MySQL 数据访问链路不受影响，Service/Controller/Worker/检索层的现有行为和 API 保持不变。

## 7. 非目标

- 不修改 Flyway 迁移机制或迁移脚本格式；
- 不把 MySQL Mapper 迁移到新的会话工厂；
- 不改造知识库 Service 的事务边界和业务流程；
- 不新增前端功能或接口字段。
