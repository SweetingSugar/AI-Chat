# PostgreSQL 独立数据源 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留 MySQL 默认业务数据源、MyBatis-Plus 和 Flyway 链路不变的前提下，增加可独立注入的 PostgreSQL `DataSource` 与 `JdbcTemplate`。

**Architecture:** 启动模块显式创建原有 MySQL `dataSource` 并标记为 `@Primary`，避免新增第二个数据源后 Spring Boot 停止自动创建主数据源。配置类同时从 `spring.datasource` 和 `app.datasource.postgresql` 绑定两个连接池，并只为 PostgreSQL 暴露具名 `JdbcTemplate`；MyBatis-Plus 和 Flyway 继续按默认主数据源连接 MySQL。

**Tech Stack:** Java 17、Spring Boot 3.5.16、HikariCP、Spring JDBC、PostgreSQL JDBC Driver、JUnit 5、AssertJ、Maven。

---

## 文件变更地图

- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/DataSourceConfiguration.java` — 显式装配 MySQL 主数据源、PostgreSQL 数据源和 PostgreSQL `JdbcTemplate`。
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/DataSourceConfigurationTest.java` — 在不连接真实数据库的情况下验证数据源绑定、主数据源选择和具名模板。
- Modify: `ai-platform-boot/pom.xml` — 增加 Spring JDBC 与 PostgreSQL 驱动依赖。
- Modify: `ai-platform-boot/src/main/resources/application.yml` — 增加 PostgreSQL 连接配置和 `POSTGRES_*` 环境变量入口。
- Modify: `docs/backend-development-guide.md` — 补充 PostgreSQL 独立数据源配置及其不参与现有迁移链路的说明。

### Task 1: Add failing data-source configuration tests

**Files:**
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/DataSourceConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

Create the test with `ApplicationContextRunner`, which builds only the explicit configuration and therefore never opens a network connection:

```java
package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DataSourceConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat",
                    "spring.datasource.username=root",
                    "spring.datasource.password=test",
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "app.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat",
                    "app.datasource.postgresql.username=postgres",
                    "app.datasource.postgresql.password=test",
                    "app.datasource.postgresql.driver-class-name=org.postgresql.Driver");

    @Test
    void keepsMySqlAsPrimaryDataSource() {
        contextRunner.run(context -> {
            DataSource primaryDataSource = context.getBean(DataSource.class);
            DataSource mysqlDataSource = context.getBean("dataSource", DataSource.class);
            HikariDataSource hikariDataSource = (HikariDataSource) mysqlDataSource;

            assertThat(primaryDataSource).isSameAs(mysqlDataSource);
            assertThat(hikariDataSource.getJdbcUrl()).isEqualTo("jdbc:mysql://localhost:3306/ai_chat");
        });
    }

    @Test
    void createsNamedPostgresqlDataSourceAndJdbcTemplate() {
        contextRunner.run(context -> {
            HikariDataSource postgresqlDataSource = context.getBean("postgresqlDataSource", HikariDataSource.class);
            JdbcTemplate postgresqlJdbcTemplate = context.getBean("postgresqlJdbcTemplate", JdbcTemplate.class);

            assertThat(postgresqlDataSource.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/ai_chat");
            assertThat(postgresqlJdbcTemplate.getDataSource()).isSameAs(postgresqlDataSource);
            assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
        });
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
mvn -pl ai-platform-boot -Dtest=DataSourceConfigurationTest test
```

Expected: FAIL during test compilation because `DataSourceConfiguration` and the PostgreSQL JDBC/Spring JDBC classes are not yet available in the boot module.

### Task 2: Add JDBC and PostgreSQL dependencies

**Files:**
- Modify: `ai-platform-boot/pom.xml:44-63`

- [ ] **Step 1: Add the Spring JDBC starter**

Add this dependency alongside the existing Spring Boot starters:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

- [ ] **Step 2: Add the PostgreSQL runtime driver**

Add this dependency after the existing MySQL runtime driver; its version comes from Spring Boot dependency management:

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

Keep `com.mysql:mysql-connector-j`, `org.flywaydb:flyway-core`, and `org.flywaydb:flyway-mysql` unchanged.

- [ ] **Step 3: Resolve dependencies and rerun the focused test**

Run:

```bash
mvn -pl ai-platform-boot -Dtest=DataSourceConfigurationTest test
```

Expected: test compilation now reaches the missing `DataSourceConfiguration` reference; the Spring JDBC, HikariCP, and PostgreSQL classes resolve successfully.

### Task 3: Implement explicit MySQL and PostgreSQL data-source configuration

**Files:**
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/DataSourceConfiguration.java`

- [ ] **Step 1: Add the configuration class**

Create the class with the following implementation:

```java
package com.example.aichat.boot.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfiguration {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource dataSource(
            @Qualifier("dataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @ConfigurationProperties("app.datasource.postgresql")
    public DataSourceProperties postgresqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "postgresqlDataSource")
    @ConfigurationProperties("app.datasource.postgresql.hikari")
    public HikariDataSource postgresqlDataSource(
            @Qualifier("postgresqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "postgresqlJdbcTemplate")
    public JdbcTemplate postgresqlJdbcTemplate(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
```

The `DataSourceProperties` builders preserve the `url` property when constructing Hikari data sources. `@Primary` is applied only to the MySQL data source and its properties; PostgreSQL is always selected by its qualifier.

- [ ] **Step 2: Run the focused tests**

Run:

```bash
mvn -pl ai-platform-boot -Dtest=DataSourceConfigurationTest test
```

Expected: PASS for both tests without requiring a running MySQL or PostgreSQL server. Hikari may log that pools are starting/stopping; no query should be executed.

### Task 4: Add PostgreSQL application configuration

**Files:**
- Modify: `ai-platform-boot/src/main/resources/application.yml:29-59`

- [ ] **Step 1: Add the independent PostgreSQL configuration block**

Add this top-level block immediately before the existing `spring:` block; do not place it under `spring.datasource`:

```yaml
app:
  datasource:
    postgresql:
      url: ${POSTGRES_URL:jdbc:postgresql://localhost:5432/ai_chat}
      username: ${POSTGRES_USERNAME:postgres}
      password: ${POSTGRES_PASSWORD:12345678}
      driver-class-name: org.postgresql.Driver
```

Keep the current MySQL block exactly as the `spring.datasource` configuration, including `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD`, and `com.mysql.cj.jdbc.Driver`. Keep `spring.flyway` unchanged so Flyway remains attached to the MySQL primary data source.

- [ ] **Step 2: Verify YAML and focused tests with the application values**

Run:

```bash
mvn -pl ai-platform-boot -Dtest=DataSourceConfigurationTest test
```

Expected: PASS; the explicit test properties continue to bind to the same two named data sources.

### Task 5: Document the two-database configuration boundary

**Files:**
- Modify: `docs/backend-development-guide.md:221-236`

- [ ] **Step 1: Add PostgreSQL to the infrastructure configuration table**

Add a row after the MySQL row:

```markdown
| PostgreSQL | `POSTGRES_URL`、`POSTGRES_USERNAME`、`POSTGRES_PASSWORD` | 独立 `postgresqlDataSource` 和 `postgresqlJdbcTemplate`，不参与现有 MyBatis-Plus 与 Flyway 链路 |
```

- [ ] **Step 2: Add the injection rule below the table**

Add this sentence before the existing Flyway paragraph:

```markdown
需要访问 PostgreSQL 时，通过 `@Qualifier("postgresqlJdbcTemplate")` 注入对应的 `JdbcTemplate`；MySQL 仍是默认主数据源，现有 Mapper 和 Flyway 迁移不变。
```

### Task 6: Run full verification

**Files:**
- No additional files.

- [ ] **Step 1: Run all Maven tests**

Run:

```bash
mvn test
```

Expected: PASS across all modules. The command must not require a running PostgreSQL instance; tests should not execute database queries.

- [ ] **Step 2: Build the executable boot module**

Run:

```bash
mvn -pl ai-platform-boot -am package -DskipTests
```

Expected: PASS, with the packaged boot artifact containing both MySQL and PostgreSQL runtime drivers.

- [ ] **Step 3: Inspect the final diff and working tree**

Run:

```bash
git diff --check
git status --short
git diff -- ai-platform-boot/pom.xml ai-platform-boot/src/main/resources/application.yml ai-platform-boot/src/main/java/com/example/aichat/boot/config/DataSourceConfiguration.java ai-platform-boot/src/test/java/com/example/aichat/boot/config/DataSourceConfigurationTest.java docs/backend-development-guide.md
```

Expected: no whitespace errors; only the planned dependency, configuration, test, implementation, and documentation changes are present. Do not add or commit unrelated `.DS_Store` files.
