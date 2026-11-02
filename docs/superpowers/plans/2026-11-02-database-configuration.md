# Database Configuration Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MySQL the unambiguous default database while isolating all knowledge-base PostgreSQL access behind explicitly named data sources, MyBatis sessions, transactions, and migrations.

**Architecture:** Split the current generic data-source configuration into a primary MySQL configuration and a knowledge-scoped PostgreSQL configuration. Give each database its own explicitly named MyBatis factory, session template, and transaction manager; ordinary Mapper scanning binds to MySQL and knowledge Mapper scanning binds to PostgreSQL. Keep the existing knowledge PostgreSQL schema and SQL unchanged.

**Tech Stack:** Spring Boot 3.5, Spring JDBC/HikariCP, MyBatis-Plus 3.5.17, Flyway, JUnit 5, AssertJ, Maven.

---

## File map

- Create `ai-platform-boot/src/main/java/com/example/aichat/boot/config/MysqlDataSourceConfiguration.java` — binds `spring.datasource` and exposes the primary MySQL data source.
- Create `ai-platform-boot/src/main/java/com/example/aichat/boot/config/KnowledgePostgresqlDataSourceConfiguration.java` — binds `knowledge.datasource.postgresql` and exposes the non-primary knowledge PostgreSQL data source.
- Delete `ai-platform-boot/src/main/java/com/example/aichat/boot/config/DataSourceConfiguration.java` — replace the mixed configuration with the two focused files above.
- Modify `ai-platform-boot/src/main/java/com/example/aichat/boot/config/MysqlMybatisConfiguration.java` — rename the MySQL beans, bind ordinary Mapper scanning to MySQL, and load only ordinary Mapper XML files.
- Modify `ai-platform-boot/src/main/java/com/example/aichat/boot/config/PostgresqlMybatisConfiguration.java` — rename the PostgreSQL beans and point them at the knowledge-scoped data source.
- Modify `ai-platform-boot/src/main/java/com/example/aichat/boot/config/KnowledgeDatabaseMigrationConfiguration.java` — use the knowledge PostgreSQL data source and the new migration property.
- Modify `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java` — remove the application-level Mapper scan after the two MyBatis configurations own their scans.
- Modify `ai-platform-boot/src/main/resources/application.yml` — move PostgreSQL properties under `knowledge.datasource.postgresql` and rename the migration environment variable.
- Modify `ai-platform-boot/src/test/java/com/example/aichat/boot/config/DataSourceConfigurationTest.java` — test the split data-source configurations and explicit bean names.
- Modify `ai-platform-boot/src/test/java/com/example/aichat/boot/config/PostgresqlMybatisConfigurationTest.java` — verify both MyBatis factories, templates, transaction managers, and Mapper boundaries.
- Modify `ai-platform-boot/src/test/java/com/example/aichat/boot/config/KnowledgeDatabaseMigrationConfigurationTest.java` — verify the new property prefix and PostgreSQL data-source binding.
- Modify `docs/backend-development-guide.md` — document the final property prefixes and explicit database responsibilities.

## Task 1: Add failing tests for the split data-source contract

**Files:**
- Modify: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/DataSourceConfigurationTest.java`
- Modify: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/PostgresqlMybatisConfigurationTest.java`
- Modify: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/KnowledgeDatabaseMigrationConfigurationTest.java`

- [ ] **Step 1: Replace old data-source names and prefixes in the test fixtures.**

Change every PostgreSQL test property from:

```text
app.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat
app.datasource.postgresql.username=postgres
app.datasource.postgresql.password=test
app.datasource.postgresql.driver-class-name=org.postgresql.Driver
app.datasource.postgresql.hikari.initialization-fail-timeout=-1
```

to:

```text
knowledge.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat
knowledge.datasource.postgresql.username=postgres
knowledge.datasource.postgresql.password=test
knowledge.datasource.postgresql.driver-class-name=org.postgresql.Driver
knowledge.datasource.postgresql.hikari.initialization-fail-timeout=-1
```

- [ ] **Step 2: Make `DataSourceConfigurationTest` assert the new explicit names.**

Use `MysqlDataSourceConfiguration.class` and `KnowledgePostgresqlDataSourceConfiguration.class` in the context. Replace the primary assertions with:

```java
DataSource primary = context.getBean(DataSource.class);
HikariDataSource mysql = context.getBean("mysqlDataSource", HikariDataSource.class);
HikariDataSource knowledgePostgresql = context.getBean(
        "knowledgePostgresqlDataSource", HikariDataSource.class);

assertThat(primary).isSameAs(mysql);
assertThat(mysql.getJdbcUrl()).startsWith("jdbc:mysql://localhost:3306/ai_chat");
assertThat(knowledgePostgresql.getJdbcUrl())
        .isEqualTo("jdbc:postgresql://localhost:5432/ai_chat");
assertThat(context.getBeansOfType(DataSource.class)).hasSize(2);
```

Remove the `JdbcTemplate` import and the assertion for `postgresqlJdbcTemplate`; no production code uses that third access path.

- [ ] **Step 3: Update the MyBatis test expectations before changing production configuration.**

Change the test context to use the two new data-source configuration classes and assert these names:

```java
SqlSessionFactory mysqlFactory = context.getBean(
        "mysqlSqlSessionFactory", SqlSessionFactory.class);
SqlSessionTemplate mysqlTemplate = context.getBean(
        "mysqlSqlSessionTemplate", SqlSessionTemplate.class);
SqlSessionFactory knowledgeFactory = context.getBean(
        "knowledgePostgresqlSqlSessionFactory", SqlSessionFactory.class);
SqlSessionTemplate knowledgeTemplate = context.getBean(
        "knowledgePostgresqlSqlSessionTemplate", SqlSessionTemplate.class);
```

Add `UserMapper` and assert that the MySQL factory contains a user statement while the PostgreSQL factory contains a knowledge statement:

```java
assertThat(mysqlFactory.getConfiguration().getMappedStatementNames())
        .anyMatch(name -> name.startsWith("com.example.aichat.system.mapper.UserMapper."));
assertThat(mysqlFactory.getConfiguration().getMappedStatementNames())
        .noneMatch(name -> name.startsWith("com.example.aichat.knowledge.mapper."));
assertThat(knowledgeFactory.getConfiguration().getMappedStatementNames())
        .anyMatch(name -> name.startsWith("com.example.aichat.knowledge.mapper."));
assertThat(knowledgeFactory.getConfiguration().getMappedStatementNames())
        .noneMatch(name -> name.startsWith("com.example.aichat.system.mapper."));
assertThat(context.getBean(UserMapper.class)).isNotNull();
assertThat(context.getBean(KnowledgeBaseMapper.class)).isNotNull();
```

Assert the data-source and transaction-manager relationships by explicit bean name:

```java
assertThat(mysqlFactory.getConfiguration().getEnvironment().getDataSource())
        .isSameAs(context.getBean("mysqlDataSource"));
assertThat(knowledgeFactory.getConfiguration().getEnvironment().getDataSource())
        .isSameAs(context.getBean("knowledgePostgresqlDataSource"));
assertThat(mysqlTemplate.getSqlSessionFactory()).isSameAs(mysqlFactory);
assertThat(knowledgeTemplate.getSqlSessionFactory()).isSameAs(knowledgeFactory);
assertThat(context.getBean("mysqlTransactionManager", DataSourceTransactionManager.class)
        .getDataSource()).isSameAs(context.getBean("mysqlDataSource"));
assertThat(context.getBean("knowledgePostgresqlTransactionManager",
        DataSourceTransactionManager.class).getDataSource())
        .isSameAs(context.getBean("knowledgePostgresqlDataSource"));
```

- [ ] **Step 4: Change the migration test to the new property and data-source name.**

Use `KnowledgePostgresqlDataSourceConfiguration.class` and set:

```text
knowledge.postgresql.migration-enabled=false
```

Keep the assertion that the context does not create `knowledgeFlyway`.

- [ ] **Step 5: Run the focused tests and confirm the failure is caused by the missing new configuration.**

Run:

```bash
mvn -o -pl ai-platform-boot -am \
  -Dtest=DataSourceConfigurationTest,PostgresqlMybatisConfigurationTest,KnowledgeDatabaseMigrationConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation or context failures referring to the new configuration classes/bean names. Do not change the assertions to match the old implementation; these failures establish the new contract.

## Task 2: Split the data-source configuration

**Files:**
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/MysqlDataSourceConfiguration.java`
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/KnowledgePostgresqlDataSourceConfiguration.java`
- Delete: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/DataSourceConfiguration.java`

- [ ] **Step 1: Create the primary MySQL data-source configuration.**

Implement:

```java
@Configuration(proxyBeanMethods = false)
public class MysqlDataSourceConfiguration {

    @Bean(name = "mysqlDataSourceProperties")
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties mysqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "mysqlDataSource")
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource mysqlDataSource(
            @Qualifier("mysqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
```

- [ ] **Step 2: Create the knowledge PostgreSQL data-source configuration.**

Implement the same `DataSourceProperties`/`HikariDataSource` pattern with these exact names and prefix:

```java
@Configuration(proxyBeanMethods = false)
public class KnowledgePostgresqlDataSourceConfiguration {

    @Bean(name = "knowledgePostgresqlDataSourceProperties")
    @ConfigurationProperties("knowledge.datasource.postgresql")
    DataSourceProperties knowledgePostgresqlDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "knowledgePostgresqlDataSource")
    @ConfigurationProperties("knowledge.datasource.postgresql.hikari")
    HikariDataSource knowledgePostgresqlDataSource(
            @Qualifier("knowledgePostgresqlDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
```

Do not add a `JdbcTemplate` bean here.

- [ ] **Step 3: Delete the old mixed configuration.**

Remove `DataSourceConfiguration.java` only after both new configuration classes exist and all focused tests refer to them.

- [ ] **Step 4: Run the data-source test.**

Run:

```bash
mvn -o -pl ai-platform-boot -am -Dtest=DataSourceConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `DataSourceConfigurationTest` passes and confirms exactly two data sources, with MySQL selected as the primary data source.

## Task 3: Give each MyBatis stack an explicit database boundary

**Files:**
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/MysqlMybatisConfiguration.java`
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/PostgresqlMybatisConfiguration.java`
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`

- [ ] **Step 1: Bind ordinary Mapper scanning to the MySQL template.**

Add `@MapperScan` to `MysqlMybatisConfiguration` with these packages and reference:

```java
@MapperScan(
        basePackages = {
                "com.example.aichat.system.mapper",
                "com.example.aichat.ai.mapper",
                "com.example.aichat.chat.mapper",
                "com.example.aichat.admin.mapper",
                "com.example.aichat.file.mapper"
        },
        sqlSessionTemplateRef = "mysqlSqlSessionTemplate")
```

- [ ] **Step 2: Rename and rebind the MySQL factory, template, and transaction manager.**

Use `@Qualifier("mysqlDataSource")` and these exact names:

```java
@Bean(name = "mysqlSqlSessionFactory")
@Primary
SqlSessionFactory mysqlSqlSessionFactory(
        @Qualifier("mysqlDataSource") DataSource dataSource,
        MybatisPlusProperties properties,
        ApplicationContext applicationContext) throws Exception
```

Load only ordinary XML resources, which are all direct children of module `mapper` directories:

```java
factory.setMapperLocations(new PathMatchingResourcePatternResolver()
        .getResources("classpath*:mapper/*.xml"));
```

Retain the existing `MybatisPlusProperties` configuration application and global configuration. Register the `mysqlSqlSessionTemplate` and `mysqlTransactionManager` beans as `@Primary`, with the MySQL qualifiers.

- [ ] **Step 3: Rename and rebind the PostgreSQL stack.**

In `PostgresqlMybatisConfiguration`, keep the knowledge package scan but change the reference and bean names:

```java
@MapperScan(
        basePackages = "com.example.aichat.knowledge.mapper",
        sqlSessionTemplateRef = "knowledgePostgresqlSqlSessionTemplate")
```

Use `@Qualifier("knowledgePostgresqlDataSource")` and expose:

```java
@Bean(name = "knowledgePostgresqlSqlSessionFactory")
SqlSessionFactory knowledgePostgresqlSqlSessionFactory(
        @Qualifier("knowledgePostgresqlDataSource") DataSource dataSource) throws Exception

@Bean(name = "knowledgePostgresqlSqlSessionTemplate")
SqlSessionTemplate knowledgePostgresqlSqlSessionTemplate(
        @Qualifier("knowledgePostgresqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory)

@Bean(name = "knowledgePostgresqlTransactionManager")
DataSourceTransactionManager knowledgePostgresqlTransactionManager(
        @Qualifier("knowledgePostgresqlDataSource") DataSource dataSource)
```

Keep `mapper/postgresql/**/*.xml`, `PgVectorTypeHandler`, and the existing PostgreSQL MyBatis configuration in this class only.

- [ ] **Step 4: Remove the application-level Mapper scan.**

Delete the `MapperScan` import and annotation from `AiPlatformApplication`. The two focused configuration classes now own all Mapper scanning, preventing a scanner without an explicit session-template reference from being registered.

- [ ] **Step 5: Run the MyBatis boundary tests.**

Run:

```bash
mvn -o -pl ai-platform-boot -am -Dtest=PostgresqlMybatisConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: both MySQL and PostgreSQL configuration tests pass; `UserMapper` is present in the MySQL stack, knowledge Mappers are present only in the PostgreSQL stack, and each transaction manager points to its matching data source.

## Task 4: Isolate PostgreSQL migrations and update application properties

**Files:**
- Modify: `ai-platform-boot/src/main/resources/application.yml`
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/KnowledgeDatabaseMigrationConfiguration.java`
- Modify: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/KnowledgeDatabaseMigrationConfigurationTest.java`

- [ ] **Step 1: Move PostgreSQL configuration in `application.yml`.**

Move the existing block from `app.datasource.postgresql` to:

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

Keep the existing `knowledge.vector` task properties, but replace only the migration property with:

```yaml
postgresql:
  migration-enabled: ${KNOWLEDGE_POSTGRESQL_MIGRATION_ENABLED:false}
```

The final hierarchy must be `knowledge.postgresql.migration-enabled` and `knowledge.datasource.postgresql`.

- [ ] **Step 2: Rebind `KnowledgeDatabaseMigrationConfiguration`.**

Change the condition to:

```java
@ConditionalOnProperty(
        prefix = "knowledge.postgresql",
        name = "migration-enabled",
        havingValue = "true")
```

Change the Flyway bean method parameter to:

```java
@Qualifier("knowledgePostgresqlDataSource") DataSource dataSource
```

Keep the migration location `classpath:db/migration/knowledge-postgresql` and the bean name `knowledgeFlyway`.

- [ ] **Step 3: Update and run the migration tests.**

Use `knowledge.postgresql.migration-enabled=false` and the new data-source configuration in the disabled-case test. Add a positive binding test:

```java
@Test
void bindsKnowledgeFlywayToKnowledgePostgresqlDataSource() {
    contextRunner
            .withPropertyValues("knowledge.postgresql.migration-enabled=true")
            .run(context -> {
                Flyway flyway = context.getBean("knowledgeFlyway", Flyway.class);

                assertThat(flyway.getConfiguration().getDataSource())
                        .isSameAs(context.getBean("knowledgePostgresqlDataSource"));
            });
}
```

Run:

```bash
mvn -o -pl ai-platform-boot -am -Dtest=KnowledgeDatabaseMigrationConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: the knowledge Flyway bean is absent when the new flag is false and is bound to the knowledge PostgreSQL data source when the flag is true.

## Task 5: Update current documentation and references

**Files:**
- Modify: `docs/backend-development-guide.md`

- [ ] **Step 1: Replace the database configuration table entries.**

Document:

- `MYSQL_URL`, `MYSQL_USERNAME`, `MYSQL_PASSWORD` as the default MySQL data source;
- `POSTGRES_URL`, `POSTGRES_USERNAME`, `POSTGRES_PASSWORD` under `knowledge.datasource.postgresql` and restricted to knowledge Mappers/Flyway;
- `KNOWLEDGE_POSTGRESQL_MIGRATION_ENABLED` for the independent knowledge PostgreSQL migration;
- MySQL Mapper and transaction defaults as `mysqlSqlSessionTemplate` and `mysqlTransactionManager`;
- knowledge PostgreSQL names as `knowledgePostgresqlSqlSessionTemplate` and `knowledgePostgresqlTransactionManager`.

Remove references to `postgresqlJdbcTemplate` and the old `app.datasource.postgresql` prefix. Do not rewrite historical design/plan documents.

- [ ] **Step 2: Search for stale production references.**

Run:

```bash
rg -n -P --hidden --glob '!target/**' --glob '!.git' \
  '(app\.datasource\.postgresql|(?<!knowledge)postgresqlDataSource|(?<!knowledge)postgresqlSqlSession|(?<!knowledge)postgresqlTransactionManager|postgresqlJdbcTemplate|knowledge\.vector\.migration-enabled|KNOWLEDGE_VECTOR_MIGRATION_ENABLED)' \
  ai-platform-boot/src ai-platform-knowledge/src docs/backend-development-guide.md
```

Expected: no stale references remain in production configuration, tests, or the current backend guide. Historical design/plan files may retain their original terminology.

## Task 6: Run verification and inspect the final diff

**Files:**
- Test: all affected boot configuration tests
- Verify: `ai-platform-boot` build and the full reactor test result

- [ ] **Step 1: Run all boot configuration tests with reactor dependencies.**

```bash
mvn -o -pl ai-platform-boot -am \
  -Dtest=DataSourceConfigurationTest,PostgresqlMybatisConfigurationTest,KnowledgeDatabaseMigrationConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests pass.

- [ ] **Step 2: Run a compile/package verification.**

```bash
mvn -o -pl ai-platform-boot -am -DskipTests package
```

Expected: the boot module and all reactor dependencies compile and package successfully.

- [ ] **Step 3: Run the complete test suite.**

```bash
mvn -o test
```

Expected: the database-related tests pass. If the existing local AI provider tests still fail because Ollama/OpenAI endpoints are unavailable, report those failures separately and do not attribute them to this database configuration change.

- [ ] **Step 4: Check the final diff and whitespace.**

```bash
git diff --check
git status --short
git diff --stat
```

Confirm that only the planned configuration, tests, documentation, and plan/spec files changed; do not stage `.DS_Store`.
