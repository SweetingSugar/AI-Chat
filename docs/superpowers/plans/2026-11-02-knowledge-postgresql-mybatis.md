# 知识库 PostgreSQL MyBatis XML 迁移 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知识库模块的 PostgreSQL 业务数据访问全部迁移到独立 PostgreSQL MyBatis Mapper XML，同时保持现有 Service、Worker、检索和 MySQL 数据访问行为不变。

**Architecture:** `ai-platform-boot` 创建绑定 `postgresqlDataSource` 的独立 `SqlSessionFactory`、`SqlSessionTemplate` 和事务管理器，只扫描知识库 Mapper。知识库 Repository 保留原有领域 API，改为调用 Mapper；所有 PostgreSQL 业务 SQL 放在 `mapper/postgresql/*.xml`，Flyway 迁移仍保留为 SQL 脚本。

**Tech Stack:** Java 17、Spring Boot 3.5.16、MyBatis-Plus 3.5.17、MyBatis XML、PostgreSQL 16 + pgvector、Testcontainers、JUnit 5。

---

## 文件地图

### 新增

- `ai-platform-boot/src/main/java/com/example/aichat/boot/config/PostgresqlMybatisConfiguration.java`：PostgreSQL 专用 MyBatis 会话工厂、模板和事务管理器。
- `ai-platform-boot/src/test/java/com/example/aichat/boot/config/PostgresqlMybatisConfigurationTest.java`：验证 PostgreSQL 数据源隔离、Mapper 扫描和 XML 加载。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeBaseMapper.java`：知识库 SQL Mapper 接口。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeDocumentMapper.java`：文档 SQL Mapper 接口。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkMapper.java`：分块 SQL Mapper 接口。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkEmbeddingMapper.java`：向量写入和检索 SQL Mapper 接口。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeIndexTaskMapper.java`：索引任务 SQL Mapper 接口。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkRow.java`：携带 JSON 字符串的分块数据库投影类型。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/typehandler/PgVectorTypeHandler.java`：`float[]` 到 PostgreSQL `vector` 参数的安全绑定。
- `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeBaseMapper.xml`：知识库 SQL 和 resultMap。
- `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeDocumentMapper.xml`：文档 SQL 和 resultMap。
- `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeChunkMapper.xml`：分块 SQL 和 row resultMap。
- `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeChunkEmbeddingMapper.xml`：向量 SQL 和相似度检索 SQL。
- `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeIndexTaskMapper.xml`：任务 SQL 和 resultMap。

### 修改

- `ai-platform-knowledge/pom.xml`：加入 MyBatis-Plus starter、测试所需 Spring JDBC 和 PostgreSQL 驱动依赖，移除知识库生产代码不再需要的 JDBC starter。
- `ai-platform-boot/pom.xml`：显式声明 MyBatis-Plus starter，供 PostgreSQL 配置编译和运行。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeBaseRepository.java`：改为调用 `KnowledgeBaseMapper`。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeDocumentRepository.java`：改为调用 `KnowledgeDocumentMapper`。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeChunkRepository.java`：改为调用 `KnowledgeChunkMapper`，保留 JSON 转换。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeChunkEmbeddingRepository.java`：改为调用 `KnowledgeChunkEmbeddingMapper`，移除 JdbcTemplate 和向量 SQL。
- `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeIndexTaskRepository.java`：改为调用 `KnowledgeIndexTaskMapper`。
- `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/repository/KnowledgeRepositoryTest.java`：通过 MyBatis `SqlSessionTemplate` 构造 Repository 并验证 XML SQL。

不修改 Flyway 迁移文件、Service/Controller/Worker/检索接口和默认 MySQL Mapper 扫描配置。

---

## Task 1: 建立 PostgreSQL 专用 MyBatis 基础设施

**Files:**
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/PostgresqlMybatisConfigurationTest.java`
- Create: `ai-platform-boot/src/main/java/com/example/aichat/boot/config/PostgresqlMybatisConfiguration.java`
- Modify: `ai-platform-boot/pom.xml`
- Modify: `ai-platform-knowledge/pom.xml`

- [ ] **Step 1: 写配置失败测试**

创建 `PostgresqlMybatisConfigurationTest`，使用 `ApplicationContextRunner`、`DataSourceConfiguration` 和待创建的配置类，验证 PostgreSQL MyBatis Bean 绑定到 PostgreSQL 数据源：

```java
@Test
void createsIsolatedPostgresqlMybatisSession() {
    new ApplicationContextRunner()
            .withUserConfiguration(DataSourceConfiguration.class,
                    PostgresqlMybatisConfiguration.class)
            .withPropertyValues(
                    "spring.datasource.url=jdbc:mysql://localhost:3306/ai_chat",
                    "spring.datasource.username=root",
                    "spring.datasource.password=test",
                    "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
                    "spring.datasource.hikari.initialization-fail-timeout=-1",
                    "app.datasource.postgresql.url=jdbc:postgresql://localhost:5432/ai_chat",
                    "app.datasource.postgresql.username=postgres",
                    "app.datasource.postgresql.password=test",
                    "app.datasource.postgresql.driver-class-name=org.postgresql.Driver",
                    "app.datasource.postgresql.hikari.initialization-fail-timeout=-1")
            .run(context -> {
                SqlSessionFactory factory = context.getBean(
                        "postgresqlSqlSessionFactory", SqlSessionFactory.class);
                SqlSessionTemplate template = context.getBean(
                        "postgresqlSqlSessionTemplate", SqlSessionTemplate.class);

                assertThat(factory.getConfiguration().getMappedStatementNames())
                        .anyMatch(name -> name.startsWith(
                                "com.example.aichat.knowledge.mapper.KnowledgeBaseMapper."));
                assertThat(template.getSqlSessionFactory()).isSameAs(factory);
                assertThat(context.getBean("postgresqlTransactionManager"))
                        .isInstanceOf(DataSourceTransactionManager.class);
                assertThat(context.getBean(KnowledgeBaseMapper.class)).isNotNull();
            });
}
```

导入 `SqlSessionFactory`、`SqlSessionTemplate`、`DataSourceTransactionManager`、`KnowledgeBaseMapper` 和 AssertJ 类型。测试先应因配置类不存在而编译失败。

- [ ] **Step 2: 运行失败测试确认缺失基础设施**

运行：

```bash
mvn -pl ai-platform-boot -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PostgresqlMybatisConfigurationTest test
```

预期：编译失败，提示 `PostgresqlMybatisConfiguration` 或 PostgreSQL Mapper 尚不存在。

- [ ] **Step 3: 添加明确的 MyBatis 依赖**

在 `ai-platform-knowledge/pom.xml` 加入：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-jdbc</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

删除 `ai-platform-knowledge/pom.xml` 中生产范围的 `spring-boot-starter-jdbc`，因为知识库生产代码不再注入 `JdbcTemplate`；测试范围的 `spring-jdbc` 仍用于执行 Flyway 迁移资源初始化。

在 `ai-platform-boot/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
```

- [ ] **Step 4: 实现独立 PostgreSQL MyBatis 配置**

创建 `PostgresqlMybatisConfiguration`：

```java
@Configuration(proxyBeanMethods = false)
@MapperScan(
        basePackages = "com.example.aichat.knowledge.mapper",
        sqlSessionTemplateRef = "postgresqlSqlSessionTemplate")
public class PostgresqlMybatisConfiguration {

    @Bean(name = "postgresqlSqlSessionFactory")
    SqlSessionFactory postgresqlSqlSessionFactory(
            @Qualifier("postgresqlDataSource") DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/postgresql/**/*.xml"));

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setCacheEnabled(false);
        factory.setConfiguration(configuration);
        return factory.getObject();
    }

    @Bean(name = "postgresqlSqlSessionTemplate")
    SqlSessionTemplate postgresqlSqlSessionTemplate(
            @Qualifier("postgresqlSqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean(name = "postgresqlTransactionManager")
    DataSourceTransactionManager postgresqlTransactionManager(
            @Qualifier("postgresqlDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
```

使用 `com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean`、`com.baomidou.mybatisplus.core.MybatisConfiguration`、`org.mybatis.spring.annotation.MapperScan` 和 `PathMatchingResourcePatternResolver`。不要给该配置增加默认 MySQL Mapper 扫描包。

- [ ] **Step 5: 运行基础配置测试**

运行：

```bash
mvn -pl ai-platform-boot -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PostgresqlMybatisConfigurationTest test
```

预期：当前测试仍可能因 Mapper 接口和 XML 尚未创建而失败；下一任务完成后必须变为 PASS。此时确认依赖解析和配置类本身已编译。

---

## Task 2: 创建类型处理器、Mapper 接口和 XML

**Files:**
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeBaseMapper.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeDocumentMapper.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkMapper.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkEmbeddingMapper.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeIndexTaskMapper.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/KnowledgeChunkRow.java`
- Create: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/mapper/typehandler/PgVectorTypeHandler.java`
- Create: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeBaseMapper.xml`
- Create: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeDocumentMapper.xml`
- Create: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeChunkMapper.xml`
- Create: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeChunkEmbeddingMapper.xml`
- Create: `ai-platform-knowledge/src/main/resources/mapper/postgresql/KnowledgeIndexTaskMapper.xml`

- [ ] **Step 1: 定义 Mapper 接口契约**

创建不带 `@Mapper` 的接口，由 Task 1 的专用 `@MapperScan` 注册。所有多参数方法使用 `@Param`，接口方法只声明 SQL 操作，不包含 SQL 字符串。

`KnowledgeBaseMapper` 必须包含：

```java
List<KnowledgeBaseRecord> findPageByUserId(@Param("userId") Long userId,
                                           @Param("limit") int limit,
                                           @Param("offset") int offset);
long countByUserId(@Param("userId") Long userId);
KnowledgeBaseRecord findById(@Param("id") Long id);
KnowledgeBaseRecord findOwnedActive(@Param("userId") Long userId, @Param("id") Long id);
long countByEmbeddingModelId(@Param("modelId") Long modelId);
Long insert(@Param("userId") Long userId, @Param("name") String name,
            @Param("description") String description,
            @Param("embeddingModelId") Long embeddingModelId,
            @Param("embeddingModelVersion") Integer embeddingModelVersion);
int update(@Param("id") Long id, @Param("name") String name,
           @Param("description") String description);
int updatePendingEmbeddingModel(@Param("id") Long id,
                                @Param("embeddingModelId") Long embeddingModelId,
                                @Param("embeddingModelVersion") Integer embeddingModelVersion,
                                @Param("newIndexVersion") Long newIndexVersion);
int switchActiveEmbedding(@Param("id") Long id, @Param("indexVersion") Long indexVersion);
int switchActiveIndexVersion(@Param("id") Long id, @Param("indexVersion") Long indexVersion);
int markDeleted(@Param("userId") Long userId, @Param("id") Long id);
Long nextIndexVersion(@Param("knowledgeBaseId") Long knowledgeBaseId);
```

`KnowledgeDocumentMapper` 使用以下完整方法契约：

```java
List<KnowledgeDocumentRecord> findPageByKnowledgeBaseId(
        @Param("knowledgeBaseId") Long knowledgeBaseId,
        @Param("limit") int limit,
        @Param("offset") int offset);
long countByKnowledgeBaseId(@Param("knowledgeBaseId") Long knowledgeBaseId);
List<KnowledgeDocumentRecord> findByKnowledgeBaseId(
        @Param("knowledgeBaseId") Long knowledgeBaseId);
KnowledgeDocumentRecord findById(@Param("id") Long id);
KnowledgeDocumentRecord findActiveByKnowledgeBaseAndFile(
        @Param("knowledgeBaseId") Long knowledgeBaseId,
        @Param("fileId") Long fileId);
Long insert(@Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("sourceType") String sourceType,
            @Param("fileId") Long fileId,
            @Param("title") String title,
            @Param("rawContent") String rawContent,
            @Param("indexVersion") Long indexVersion);
int markIndexing(@Param("documentId") Long documentId,
                 @Param("indexVersion") Long indexVersion);
int markReady(@Param("documentId") Long documentId,
              @Param("indexVersion") Long indexVersion,
              @Param("chunkCount") int chunkCount);
int updateContentHash(@Param("documentId") Long documentId,
                      @Param("contentHash") String contentHash);
int markFailed(@Param("documentId") Long documentId,
               @Param("status") String status,
               @Param("errorCode") String errorCode,
               @Param("errorMessage") String errorMessage);
int markDeleted(@Param("id") Long id);
```

`markFailed` 采用单个 `status` 参数，Repository 负责把 `preserveReady` 转成 `READY` 或 `FAILED`。`sourceType` 在 Repository 中传入 `KnowledgeDocumentSourceType.name()`。

`KnowledgeChunkMapper` 使用行投影：

```java
List<KnowledgeChunkRow> findByDocumentId(@Param("documentId") Long documentId);
List<KnowledgeChunkRow> findByDocumentIdAndIndexVersion(
        @Param("documentId") Long documentId,
        @Param("indexVersion") Long indexVersion);
Long insert(@Param("documentId") Long documentId,
            @Param("indexVersion") Long indexVersion,
            @Param("chunkIndex") Integer chunkIndex,
            @Param("content") String content,
            @Param("estimatedTokens") Integer estimatedTokens,
            @Param("metadataJson") String metadataJson);
int deleteByDocumentId(@Param("documentId") Long documentId);
int deleteByDocumentAndIndexVersion(@Param("documentId") Long documentId,
                                    @Param("indexVersion") Long indexVersion);
```

`KnowledgeChunkRow` 定义为：

```java
public record KnowledgeChunkRow(Long id, Long documentId, Integer chunkIndex,
                               String content, Integer estimatedTokens,
                               String metadataJson, Long indexVersion) {
}
```

`KnowledgeChunkEmbeddingMapper` 必须包含 `int insertAll(@Param("embeddings") List<KnowledgeChunkEmbeddingRecord> embeddings)`、三个删除方法和以下检索方法：

```java
List<KnowledgeChunkSearchResult> search(
        @Param("knowledgeBaseId") Long knowledgeBaseId,
        @Param("modelId") Long modelId,
        @Param("dimension") Integer dimension,
        @Param("indexVersion") Long indexVersion,
        @Param("queryVector") float[] queryVector,
        @Param("topK") int topK);
```

`KnowledgeIndexTaskMapper` 使用以下完整方法契约：

```java
Long insert(@Param("knowledgeBaseId") Long knowledgeBaseId,
            @Param("documentId") Long documentId,
            @Param("embeddingModelId") Long embeddingModelId,
            @Param("modelVersion") Integer modelVersion,
            @Param("indexVersion") Long indexVersion,
            @Param("maxAttempts") int maxAttempts);
KnowledgeIndexTaskRecord claimNext();
int markSuccess(@Param("taskId") Long taskId);
int markFailedRetryable(@Param("taskId") Long taskId,
                        @Param("errorCode") String errorCode,
                        @Param("errorMessage") String errorMessage);
int markFailedTerminal(@Param("taskId") Long taskId,
                       @Param("errorCode") String errorCode,
                       @Param("errorMessage") String errorMessage);
int retry(@Param("taskId") Long taskId);
int recoverStale(@Param("lockedBefore") LocalDateTime lockedBefore);
long countNotSuccessful(@Param("knowledgeBaseId") Long knowledgeBaseId,
                        @Param("indexVersion") Long indexVersion);
KnowledgeIndexTaskRecord findLatestFailedByDocumentId(@Param("documentId") Long documentId);
KnowledgeIndexTaskRecord findById(@Param("taskId") Long taskId);
```


- [ ] **Step 2: 写向量类型处理器测试**

在 `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/mapper/typehandler/PgVectorTypeHandlerTest.java` 创建测试，使用 Mockito 验证参数是绑定值而非 SQL 拼接：

```java
@Test
void bindsFloatArrayAsPostgresqlVectorLiteral() throws Exception {
    PreparedStatement statement = mock(PreparedStatement.class);

    new PgVectorTypeHandler().setNonNullParameter(
            statement, 1, new float[]{1.0f, -2.5f}, JdbcType.VARCHAR);

    verify(statement).setString(1, "[1.0,-2.5]");
}
```

同时测试 `getNullableResult` 对 `null` 返回 `null`，并对 `[1.0,-2.5]` 返回两个元素。执行：

```bash
mvn -pl ai-platform-knowledge -Dtest=PgVectorTypeHandlerTest test
```

预期：在类型处理器创建前编译失败。

- [ ] **Step 3: 实现向量类型处理器**

创建 `PgVectorTypeHandler extends BaseTypeHandler<float[]>`：

- `setNonNullParameter` 使用 `PreparedStatement#setString`；
- 序列化格式固定为方括号、逗号分隔、`Locale.ROOT` 的浮点值；
- 三个 `getNullableResult` 方法读取字符串，`null` 返回 `null`，去除方括号后按逗号解析为 `float[]`；
- 不执行 SQL，不把向量值拼入 SQL。

让 Task 2 的类型处理器测试通过。

- [ ] **Step 4: 为每个 XML 编写显式 record resultMap**

每个 XML 使用标准 DTD 和正确 namespace。以知识库映射为例，resultMap 必须使用 record 构造器：

```xml
<resultMap id="knowledgeBaseResultMap"
           type="com.example.aichat.knowledge.model.KnowledgeBaseRecord">
    <constructor>
        <idArg column="id" javaType="java.lang.Long"/>
        <arg column="user_id" javaType="java.lang.Long"/>
        <arg column="name" javaType="java.lang.String"/>
        <arg column="description" javaType="java.lang.String"/>
        <arg column="embedding_model_id" javaType="java.lang.Long"/>
        <arg column="embedding_model_version" javaType="java.lang.Integer"/>
        <arg column="pending_embedding_model_id" javaType="java.lang.Long"/>
        <arg column="pending_embedding_model_version" javaType="java.lang.Integer"/>
        <arg column="pending_index_version" javaType="java.lang.Long"/>
        <arg column="active_index_version" javaType="java.lang.Long"/>
        <arg column="status"
             javaType="com.example.aichat.knowledge.enums.KnowledgeBaseStatus"/>
        <arg column="deleted" javaType="boolean"/>
        <arg column="create_time" javaType="java.time.LocalDateTime"/>
        <arg column="update_time" javaType="java.time.LocalDateTime"/>
    </constructor>
</resultMap>
```

按相同规则为文档和任务定义 resultMap；chunk 使用 `KnowledgeChunkRow` 的 `String metadataJson` 构造器；向量检索使用 `KnowledgeChunkSearchResult` 的五参数构造器。所有 select 明确列出字段，不使用 `select *`。

- [ ] **Step 5: 编写知识库、文档和 chunk XML SQL**

知识库 XML 必须把现有 Repository 的 SQL 原样迁移为参数绑定语句，包括 `limit #{limit} offset #{offset}`、逻辑删除条件、`greatest(coalesce(...), active_index_version) + 1` 和 `insert ... returning id`。插入主键使用：

```xml
<select id="insert" resultType="java.lang.Long">
    insert into knowledge_base
        (user_id, name, description, embedding_model_id, embedding_model_version)
    values (#{userId}, #{name}, #{description}, #{embeddingModelId}, #{embeddingModelVersion})
    returning id
</select>
```

文档 XML 迁移所有状态更新；状态由参数传入，禁止用 Java 字符串拼接 SQL。chunk 插入使用：

```xml
<select id="insert" resultType="java.lang.Long">
    insert into knowledge_chunk
        (document_id, index_version, chunk_index, content, estimated_tokens, chunk_metadata)
    values (#{documentId}, #{indexVersion}, #{chunkIndex}, #{content},
            #{estimatedTokens}, cast(#{metadataJson} as jsonb))
    returning id
</select>
```

- [ ] **Step 6: 编写 embedding 和 task XML SQL**

向量批量插入使用 `<foreach>` 和类型处理器：

```xml
<insert id="insertAll">
    insert into knowledge_chunk_embedding
        (chunk_id, knowledge_base_id, embedding_model_id, model_version,
         dimension, index_version, embedding)
    values
    <foreach collection="embeddings" item="item" separator=",">
        (#{item.chunkId}, #{item.knowledgeBaseId}, #{item.embeddingModelId},
         #{item.modelVersion}, #{item.dimension}, #{item.indexVersion},
         cast(#{item.embedding,
               typeHandler=com.example.aichat.knowledge.mapper.typehandler.PgVectorTypeHandler}
              as vector))
    </foreach>
</insert>
```

相似度查询必须使用绑定向量两次，并在 XML 中把 `<` 写成 `&lt;`：

```xml
select e.chunk_id, c.document_id, d.title, c.content,
       e.embedding &lt;=> cast(#{queryVector,
           typeHandler=com.example.aichat.knowledge.mapper.typehandler.PgVectorTypeHandler}
           as vector) as distance
from knowledge_chunk_embedding e
join knowledge_chunk c on c.id = e.chunk_id
join knowledge_document d on d.id = c.document_id
where e.knowledge_base_id = #{knowledgeBaseId}
  and e.embedding_model_id = #{modelId}
  and e.dimension = #{dimension}
  and e.index_version = #{indexVersion}
  and d.deleted = false
order by e.embedding &lt;=> cast(#{queryVector,
    typeHandler=com.example.aichat.knowledge.mapper.typehandler.PgVectorTypeHandler}
    as vector)
limit #{topK}
```

任务 `claimNext` 将当前 Java 文本完整迁移为一个 `<select>`：

```xml
<select id="claimNext" resultMap="knowledgeIndexTaskResultMap">
    with candidate as (
        select id
        from knowledge_index_task
        where status = 'PENDING'
          and next_retry_at &lt;= current_timestamp
          and attempt_count &lt; max_attempts
        order by id
        for update skip locked
        limit 1
    )
    update knowledge_index_task task
    set status = 'RUNNING', attempt_count = task.attempt_count + 1,
        locked_at = current_timestamp, started_at = current_timestamp,
        update_time = current_timestamp
    from candidate
    where task.id = candidate.id
    returning task.id, task.knowledge_base_id, task.document_id,
              task.embedding_model_id, task.model_version, task.index_version,
              task.status, task.attempt_count, task.max_attempts,
              task.next_retry_at, task.locked_at, task.started_at,
              task.completed_at, task.error_code, task.error_message,
              task.create_time, task.update_time
</select>
```

XML 中所有用户或向量输入必须通过 `#{...}` 绑定；不使用 `${...}`。

- [ ] **Step 7: 运行 XML 资源与 Mapper 编译检查**

运行：

```bash
mvn -pl ai-platform-knowledge -am -DskipTests compile
```

预期：五个 Mapper 接口、类型处理器和 XML 资源编译并被打包；如果 XML namespace、resultMap 构造器或 DTD 有错误，修复后再进入 Repository 改造。

---

## Task 3: 将五个 Repository 改为 Mapper facade

**Files:**
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeBaseRepository.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeDocumentRepository.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeChunkRepository.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeChunkEmbeddingRepository.java`
- Modify: `ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository/KnowledgeIndexTaskRepository.java`

- [ ] **Step 1: 替换 KnowledgeBaseRepository 依赖和查询调用**

将 `JdbcTemplate` 字段和构造器参数替换为 `KnowledgeBaseMapper`，删除 `GeneratedKeyHolder`、`KeyHolder`、`PreparedStatement` 和 `Statement` 导入。每个分页 offset 仍在 Repository 计算，再调用 `findPageByUserId(userId, size, offset)`。查询方法将 Mapper 返回的 `null` 包装为 `Optional.empty()`，计数方法保留 `null` 到 0 的兼容处理；插入直接返回 Mapper 的 `returning id` 结果。`nextIndexVersion` 继续在 Mapper 返回为空时使用 1L。

- [ ] **Step 2: 替换 KnowledgeDocumentRepository**

将构造器改为接收 `KnowledgeDocumentMapper`，移除 `SELECT_COLUMNS`、`JdbcTemplate`、PreparedStatement 和 KeyHolder。分页、详情、活动文件查询、计数和所有状态变更直接委托 XML Mapper。`findById` 和 `findActiveByKnowledgeBaseAndFile` 对 Mapper 的单条 nullable 结果调用 `Optional.ofNullable`；插入调用 Mapper 的 `returning id`；`markFailed(documentId, errorCode, errorMessage, preserveReady)` 将状态转换为 `preserveReady ? "READY" : "FAILED"` 后传给 XML。

- [ ] **Step 3: 替换 KnowledgeChunkRepository**

将构造器改为接收 `KnowledgeChunkMapper` 和 `ObjectMapper`。`findByDocumentId` 与 `findByDocumentIdAndIndexVersion` 先取得 `List<KnowledgeChunkRow>`，再通过 Repository 的 `readMetadata` 转成 `KnowledgeChunkRecord`。`insert` 先用现有 `writeMetadata` 序列化 metadata，再调用 Mapper 获取 ID，并返回包含新 ID 和原始领域字段的 record。`insertAll` 继续按顺序逐条插入，保证每个 record 都能得到数据库生成 ID；删除方法直接委托 Mapper。

保持当前 JSON 转换约定：读取失败返回 `Map.of()`，写入失败抛出 `IllegalArgumentException`。这部分不执行 SQL。

- [ ] **Step 4: 替换 KnowledgeChunkEmbeddingRepository**

将构造器改为接收 `KnowledgeChunkEmbeddingMapper`，删除 `JdbcTemplate`、`Locale` 和 `serialize` 方法。空列表仍直接返回 0；非空列表调用 XML 的 `insertAll`。三个删除方法和 `search` 直接委托 Mapper，`search` 返回 XML 映射的 `KnowledgeChunkSearchResult`。向量序列化只能由 `PgVectorTypeHandler` 完成。

- [ ] **Step 5: 替换 KnowledgeIndexTaskRepository**

将构造器改为接收 `KnowledgeIndexTaskMapper`，删除 `JdbcTemplate`、KeyHolder、PreparedStatement 和 Statement。`insert` 直接返回 Mapper 的 ID；`claimNext` 将 Mapper 的 nullable record 包装为 Optional；`markFailed` 根据 `retryable` 选择 `markFailedRetryable` 或 `markFailedTerminal`；其他更新和查询直接委托 XML。删除旧的 `rowMapper` 和 `nullableTime`，因为任务 record 已由 MyBatis resultMap 构造。

- [ ] **Step 6: 编译确认 Repository 已无直接 JDBC 依赖**

运行：

```bash
rg -n "JdbcTemplate|PreparedStatement|GeneratedKeyHolder|KeyHolder|Statement|current_timestamp|select |insert |update |delete " \
  ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository
```

预期：5 个 Repository 不再出现 JDBC 类型或 SQL 关键字；只保留 Mapper 调用、领域转换和 JSON 序列化逻辑。随后运行：

```bash
mvn -pl ai-platform-knowledge -am -DskipTests compile
```

预期：Repository 和 Mapper 接口类型一致，编译通过。

---

## Task 4: 改造 PostgreSQL 集成测试并验证 XML 行为

**Files:**
- Modify: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/repository/KnowledgeRepositoryTest.java`
- Create: `ai-platform-knowledge/src/test/java/com/example/aichat/knowledge/mapper/KnowledgeMapperXmlTest.java`
- Modify: `ai-platform-boot/src/test/java/com/example/aichat/boot/config/PostgresqlMybatisConfigurationTest.java`

- [ ] **Step 1: 为测试创建 MyBatis SessionTemplate**

在 `KnowledgeRepositoryTest` 的 `@BeforeAll` 中继续使用 `PostgreSQLContainer` 和 `DriverManagerDataSource`，通过 `ResourceDatabasePopulator` 执行 `V1__knowledge_schema.sql`；不要用 JdbcTemplate 执行业务查询。然后创建测试会话工厂：

```java
MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
factory.setDataSource(dataSource);
factory.setMapperLocations(new PathMatchingResourcePatternResolver()
        .getResources("classpath*:mapper/postgresql/**/*.xml"));
MybatisConfiguration configuration = new MybatisConfiguration();
configuration.setMapUnderscoreToCamelCase(true);
configuration.setCacheEnabled(false);
factory.setConfiguration(configuration);
SqlSessionTemplate template = new SqlSessionTemplate(factory.getObject());
```

使用 `template.getMapper(...)` 构造五个 Repository：

```java
baseRepository = new KnowledgeBaseRepository(template.getMapper(KnowledgeBaseMapper.class));
documentRepository = new KnowledgeDocumentRepository(template.getMapper(KnowledgeDocumentMapper.class));
chunkRepository = new KnowledgeChunkRepository(
        template.getMapper(KnowledgeChunkMapper.class), new ObjectMapper());
embeddingRepository = new KnowledgeChunkEmbeddingRepository(
        template.getMapper(KnowledgeChunkEmbeddingMapper.class));
taskRepository = new KnowledgeIndexTaskRepository(
        template.getMapper(KnowledgeIndexTaskMapper.class));
```

删除测试字段 `JdbcTemplate jdbcTemplate` 和所有直接 SQL 查询；保留 `DataSource` 仅用于迁移脚本初始化。

- [ ] **Step 2: 改写向量检索断言**

在 `storesAndSearchesVectorsByModelAndDimension` 中通过 `embeddingRepository.search(baseId, 20L, 3, 1L, new float[]{1, 0, 0}, 1)` 验证结果，断言只返回目标模型、维度和索引版本的记录，并断言 `chunkId`、`documentId`、`title`、`content` 和距离值。不要再用 JdbcTemplate 查询。

当前 V1 约束 `(chunk_id, index_version)` 不允许同一 chunk/版本插入两条 embedding；测试 fixture 如果需要验证模型隔离，应创建第二个 chunk 或只为同一 chunk 插入一个目标模型记录，不修改迁移文件。

- [ ] **Step 3: 增加 XML 语句覆盖测试**

创建 `KnowledgeMapperXmlTest`，继续使用 pgvector Testcontainer 和 MyBatis `SqlSessionTemplate`，至少覆盖：

- `KnowledgeBaseMapper.insert` 返回生成的 ID，`findOwnedActive` 能通过 resultMap 构造 record；
- `KnowledgeDocumentMapper.insert` 和 `markReady` 的字段映射；
- `KnowledgeChunkMapper.insert` 能写入 JSONB，查询后 Repository 能还原 metadata；
- `KnowledgeChunkEmbeddingMapper.insertAll` 能绑定 vector，`search` 能按距离排序；
- `KnowledgeIndexTaskMapper.claimNext` 第一次返回 RUNNING，第二次没有可领取任务。

每个测试只通过 Mapper/Repository API 访问业务表；迁移初始化可以使用 `ResourceDatabasePopulator`。

- [ ] **Step 4: 验证 PostgreSQL 会话与 MySQL 会话隔离**

补充 `PostgresqlMybatisConfigurationTest` 断言 PostgreSQL 会话工厂的数据源是 `postgresqlDataSource`，并且其 mapped statements 只包含 `com.example.aichat.knowledge.mapper.*`。不要把知识库包加入 `AiPlatformApplication` 当前默认 `@MapperScan`。

- [ ] **Step 5: 运行知识库集成测试**

运行：

```bash
mvn -pl ai-platform-knowledge -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PgVectorTypeHandlerTest,KnowledgeRepositoryTest,KnowledgeMapperXmlTest test
```

预期：Docker 可用时 PostgreSQL/pgvector 测试全部 PASS；Docker 不可用时 Testcontainers 按现有 `disabledWithoutDocker = true` 规则跳过集成测试，但类型处理器单元测试仍 PASS。

- [ ] **Step 6: 运行 Boot 配置测试**

运行：

```bash
mvn -pl ai-platform-boot -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=PostgresqlMybatisConfigurationTest,KnowledgeDatabaseMigrationConfigurationTest,DataSourceConfigurationTest test
```

预期：PostgreSQL MyBatis Bean、现有双数据源配置和 Flyway 禁用条件测试全部 PASS。

---

## Task 5: 完整静态检查和回归验证

**Files:**
- No new production files.

- [ ] **Step 1: 检查知识库生产代码没有直接 JDBC 业务访问**

运行：

```bash
rg -n "JdbcTemplate|NamedParameterJdbcTemplate|JdbcClient|PreparedStatement|GeneratedKeyHolder|KeyHolder" \
  ai-platform-knowledge/src/main/java
rg -n "(select|insert|update|delete)\\s" \
  ai-platform-knowledge/src/main/java/com/example/aichat/knowledge/repository
```

预期：第一条命令无输出；第二条命令无 SQL 语句输出。Flyway SQL、Mapper XML 和测试初始化代码不纳入检查。

- [ ] **Step 2: 运行受影响模块回归测试**

运行：

```bash
mvn -pl ai-platform-ai,ai-platform-file,ai-platform-knowledge,ai-platform-chat -am test
```

预期：AI、文件、知识库和聊天模块既有测试全部通过，Service 层不需要因 Repository API 改变而修改。

- [ ] **Step 3: 运行完整 Maven 验证**

运行：

```bash
mvn clean verify
```

预期：所有模块编译、测试和 Boot 打包通过；不使用 `-DskipTests`、`-DskipITs` 或跳过验证的参数。若本地 Docker 不可用，只允许已有 Testcontainers 测试按项目现有规则跳过，不能屏蔽其他测试失败。

- [ ] **Step 4: 检查差异和敏感文件**

运行：

```bash
git diff --check
git status --short
git diff --stat
```

确认新增 XML、Mapper、配置和测试文件均在差异中；不包含凭证、临时数据库文件或前端改动。不要删除或覆盖当前工作区中与本任务无关的未提交改动。

- [ ] **Step 5: 用户授权后再提交**

本计划不自动创建 Git 提交。只有用户明确要求提交时，才按实际改动逐文件 `git add`，先检查 staged diff，再创建带有以下署名的提交：

```text
Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

---

## 验收清单

- [ ] PostgreSQL 专用 `SqlSessionFactory` 使用 `postgresqlDataSource`。
- [ ] PostgreSQL Mapper 不进入默认 MySQL 会话工厂。
- [ ] 5 个知识库 Repository 不再注入或执行 `JdbcTemplate`/`PreparedStatement`。
- [ ] 所有知识库业务 SQL 均位于 `mapper/postgresql/*.xml`。
- [ ] `returning id`、JSONB、pgvector、相似度检索和 `skip locked` 均通过 XML 工作。
- [ ] 向量和用户输入全部使用参数绑定，XML 不使用 `${...}`。
- [ ] Service、Worker、检索层公共 API 和行为保持不变。
- [ ] 知识库、Boot 以及受影响模块测试通过。
