# PostgreSQL 独立数据源设计

## 背景

当前应用使用 MySQL 作为默认业务数据库，Spring Boot 的默认 `DataSource`、MyBatis-Plus、Mapper 和 Flyway 均围绕 MySQL 配置。后续开发需要访问 PostgreSQL，但不能改变现有 MySQL 业务链路。

## 目标

- 保留 MySQL 作为默认主数据源。
- 增加可独立注入的 PostgreSQL 数据源和 `JdbcTemplate`。
- 通过环境变量配置 PostgreSQL 连接信息。
- PostgreSQL 不参与现有 MyBatis-Plus Mapper 和 Flyway 迁移。
- PostgreSQL 不可用时，不因启动阶段主动查询而影响现有 MySQL 应用启动。

## 非目标

- 不迁移现有 MySQL 表或数据。
- 不改写现有 MySQL Flyway 迁移脚本。
- 不新增 PostgreSQL 业务表、迁移脚本或示例查询。
- 不将 MyBatis-Plus 切换为 PostgreSQL，也不改变其 `DbType.MYSQL` 配置。

## 方案

### 数据源边界

MySQL 继续使用 `spring.datasource` 配置，并由应用配置类显式创建名为 `dataSource` 的 `@Primary` 数据源。新增 PostgreSQL 使用独立配置前缀 `app.datasource.postgresql`，创建名为 `postgresqlDataSource` 的数据源，不标记为 `@Primary`。显式声明 MySQL 主数据源是必要的，因为注册第二个自定义 `DataSource` 后，Spring Boot 不会再自动创建原有主数据源。现有 MyBatis-Plus、Flyway 和 Mapper 继续使用 MySQL。

基于 PostgreSQL 数据源创建名为 `postgresqlJdbcTemplate` 的 `JdbcTemplate`。后续访问 PostgreSQL 的业务代码必须通过 `@Qualifier("postgresqlJdbcTemplate")` 注入，避免与默认数据源相关的 `JdbcTemplate` 混淆。

### 依赖

在 `ai-platform-boot` 中增加 PostgreSQL JDBC 驱动和 `spring-boot-starter-jdbc`。保留 MySQL 驱动、Flyway 核心和 `flyway-mysql`，不改变现有数据库迁移链路。

### 配置

在 `application.yml` 增加以下配置域：

```yaml
app:
  datasource:
    postgresql:
      url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/ai_chat}
      username: ${POSTGRES_USERNAME:postgres}
      password: ${POSTGRES_PASSWORD:12345678}
      driver-class-name: org.postgresql.Driver
```

PostgreSQL 配置不放入 `spring.datasource`，避免被识别为应用主数据源。使用 Spring Boot `DataSourceProperties` 绑定配置并构建连接池，规避 Hikari `url` 与 `jdbcUrl` 属性绑定差异。

### 配置类位置

PostgreSQL 数据源配置类放在 `ai-platform-boot`，由启动模块统一装配；业务模块通过 Spring Bean 使用，不需要把数据源装配职责下沉到公共模块。配置类显式提供原有 MySQL 主数据源和两个 PostgreSQL 相关具名 Bean：

- `dataSource`：绑定 `spring.datasource`，标记为 `@Primary`，供 MyBatis-Plus、Flyway 和默认 JDBC 自动配置使用；
- `postgresqlDataSource`：绑定 `app.datasource.postgresql`，不标记为 `@Primary`；
- `postgresqlJdbcTemplate`：基于 `postgresqlDataSource` 创建。

Spring 管理连接池生命周期，并在应用关闭时释放资源。配置创建不执行连接查询，不将 PostgreSQL 健康状态作为现有应用启动的前置条件。

## 使用方式

访问 PostgreSQL 的代码使用具名 `JdbcTemplate`，例如通过 `@Qualifier("postgresqlJdbcTemplate")` 注入。MySQL 相关代码保持现有注入和 Mapper 用法不变。

## 验证

- 增加配置类测试，验证 MySQL `dataSource` 仍为 `@Primary`，并验证 PostgreSQL 数据源、`JdbcTemplate` Bean 的名称及 PostgreSQL JDBC URL。
- 执行完整 Maven 测试，确认现有 MySQL Mapper、Flyway 相关配置和应用模块测试不受影响。
- 不要求本地必须启动 PostgreSQL 实例；测试不应依赖真实 PostgreSQL 网络连接。
