# 数据库配置分层设计

## 背景

应用同时使用 MySQL 和 PostgreSQL。MySQL 保存系统、AI、聊天、文件和管理模块的业务数据；知识库使用 PostgreSQL 与 pgvector。当前两套数据源、MyBatis 会话工厂、事务管理器和 Flyway 配置存在通用命名与默认注入路径，导致普通 Mapper 可能错误绑定 PostgreSQL。

典型问题是认证接口的 `UserMapper` 未显式绑定主会话模板，在 PostgreSQL 会话工厂存在时被错误装配到 PostgreSQL。

## 目标

- MySQL 是整个应用的默认数据库。
- 普通模块的 Mapper、未指定事务管理器的事务和主 Flyway 都明确使用 MySQL。
- 知识库的 Mapper、PostgreSQL 专用 TypeHandler、知识库事务和知识库 Flyway 都明确使用 PostgreSQL。
- 两套数据源可以同时存在，但不依赖 Spring 按类型猜测数据源或会话模板。
- 不改变现有业务接口、知识库 SQL 和数据库表结构。

## 非目标

- 不将知识库迁移到 MySQL。
- 不引入路由数据源或运行时动态切换机制。
- 不修改 PostgreSQL 的 pgvector 检索逻辑。
- 不改变知识库始终启用的现有模块边界。

## 配置结构

MySQL 继续使用 Spring Boot 的标准配置前缀，作为全局默认数据源：

```yaml
spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://localhost:3306/ai_chat?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${MYSQL_USERNAME:root}
    password: ${MYSQL_PASSWORD:12345678}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

PostgreSQL 配置移动到知识库专用前缀：

```yaml
knowledge:
  datasource:
    postgresql:
      url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/ai_chat}
      username: ${POSTGRES_USERNAME:ice}
      password: ${POSTGRES_PASSWORD:}
      driver-class-name: org.postgresql.Driver
      hikari:
        initialization-fail-timeout: -1
```

知识库迁移开关从 `knowledge.vector.migration-enabled` 调整为 `knowledge.postgresql.migration-enabled`，环境变量从 `KNOWLEDGE_VECTOR_MIGRATION_ENABLED` 调整为 `KNOWLEDGE_POSTGRESQL_MIGRATION_ENABLED`，默认值保持关闭。向量任务开关 `knowledge.vector.enabled` 仍只控制任务调度，不再承担数据库迁移语义。

## Bean 分层

### MySQL 主链路

新增或调整 MySQL 配置，使以下 Bean 使用 `spring.datasource` 创建，并全部标记为 `@Primary`：

- `mysqlDataSource`
- `mysqlSqlSessionFactory`
- `mysqlSqlSessionTemplate`
- `mysqlTransactionManager`

普通模块的 Mapper 扫描显式指定 `mysqlSqlSessionTemplate`。MySQL 会话工厂只加载普通模块的 Mapper XML，不加载 `mapper/postgresql/**/*.xml`；默认事务管理器使用 `mysqlTransactionManager`，因此现有未指定事务管理器的 `@Transactional` 代码仍然以 MySQL 为事务边界。

### 知识库 PostgreSQL 链路

知识库 PostgreSQL 配置从 `knowledge.datasource.postgresql` 创建以下非主 Bean：

- `knowledgePostgresqlDataSource`
- `knowledgePostgresqlSqlSessionFactory`
- `knowledgePostgresqlSqlSessionTemplate`
- `knowledgePostgresqlTransactionManager`

知识库 Mapper 扫描显式指定 `knowledgePostgresqlSqlSessionTemplate`。`PgVectorTypeHandler` 和 `mapper/postgresql/**/*.xml` 只加载到知识库 PostgreSQL 会话工厂中。

当前没有生产代码使用的通用 `postgresqlJdbcTemplate` 将被删除，避免形成绕过知识库边界的第三条访问路径。

## 数据访问与迁移流程

应用启动时创建两套数据源，但只有 MySQL 数据源参与默认注入：

1. MySQL 主会话工厂扫描系统、AI、聊天、文件和管理模块 Mapper。
2. PostgreSQL 专用会话工厂只扫描 `com.example.aichat.knowledge.mapper`。
3. 普通 Flyway 使用 `mysqlDataSource`，迁移位置为 `classpath:db/migration`。
4. 知识库 Flyway 使用 `knowledgePostgresqlDataSource`，迁移位置为 `classpath:db/migration/knowledge-postgresql`。
5. 知识库事务如需显式事务边界，使用 `knowledgePostgresqlTransactionManager`；普通业务继续使用主 MySQL 事务管理器。

由于知识库接口始终启用，知识库 PostgreSQL 数据源和 Mapper 会随应用启动；“按需使用”表示只有知识库访问路径使用 PostgreSQL，而不是在当前模块边界下延迟创建 PostgreSQL Bean。

## 测试设计

新增或调整配置测试，验证以下约束：

- MySQL 数据源从 `spring.datasource` 读取并为主数据源。
- PostgreSQL 数据源从 `knowledge.datasource.postgresql` 读取且不是主数据源。
- MySQL 会话工厂和默认事务管理器绑定 MySQL 数据源。
- 知识库会话工厂和知识库事务管理器绑定 PostgreSQL 数据源。
- 普通 Mapper（尤其是 `UserMapper`）绑定 MySQL 会话模板。
- 知识库 Mapper 绑定 PostgreSQL 会话模板。
- `PgVectorTypeHandler` 只注册在知识库会话工厂。
- 两套 Flyway 分别使用正确的数据源、迁移目录和迁移开关。

验收标准：不修改 `MYSQL_*` 配置时，`/api/auth/login` 以及其他普通业务查询使用 MySQL；知识库 Mapper 只能使用 PostgreSQL；同时注册两套数据源时不存在默认 Mapper 或默认事务管理器的歧义。
