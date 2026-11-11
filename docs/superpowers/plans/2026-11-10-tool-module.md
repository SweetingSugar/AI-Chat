# Tool Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增独立的 `ai-platform-tool` 模块，实现 HTTP Tool、Streamable HTTP MCP Server、统一 `toolCode` 执行、后台管理和基础执行日志。

**Architecture:** `ai-platform-tool` 持有工具领域模型、MyBatis 持久化、凭证处理、HTTP/MCP 执行器和统一服务接口；`ai-platform-admin` 只提供管理控制器和权限。HTTP 与 MCP 都实现 `ToolExecutor`，统一执行服务按 `toolCode` 加载启用工具并路由到对应执行器。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring MVC `RestClient`、MyBatis-Plus、MySQL/Flyway、Sa-Token、Jackson、官方 Java MCP SDK `io.modelcontextprotocol.sdk:mcp:0.18.3`、JUnit 5、Mockito、JDK `HttpServer`、Testcontainers MySQL。

---

## 文件结构与边界

### 新增 `ai-platform-tool`

- `pom.xml`：模块依赖；依赖 `ai-platform-common`、`ai-platform-ai`（复用 `AiCredentialCipher`）、Web、Validation、MyBatis-Plus、Sa-Token 和 MCP SDK。
- `src/main/java/com/example/aichat/tool/entity/`：`ToolDefinition`、`ToolMcpServer`、`ToolInvocationLog`。
- `src/main/java/com/example/aichat/tool/enums/`：工具类型、来源、可用性、认证方式、HTTP 方法、调用状态和发现状态。
- `src/main/java/com/example/aichat/tool/mapper/`：三个 MyBatis-Plus Mapper。
- `src/main/java/com/example/aichat/tool/dto/`：执行请求/响应、管理请求/视图和发现结果。
- `src/main/java/com/example/aichat/tool/api/`：运行时、管理和发现服务接口。
- `src/main/java/com/example/aichat/tool/service/`：管理、发现、执行、日志服务实现。
- `src/main/java/com/example/aichat/tool/executor/`：`ToolExecutor`、HTTP 执行器和 MCP 执行器。
- `src/main/java/com/example/aichat/tool/mcp/`：MCP 客户端工厂和 MCP 会话适配。
- `src/main/java/com/example/aichat/tool/validation/`：URL SSRF 校验、模板解析和认证 Header 编解码。
- `src/main/java/com/example/aichat/tool/config/`：`ToolProperties` 和 Bean 配置。
- `src/main/java/com/example/aichat/tool/controller/ToolExecutionController.java`：运行时统一执行接口。
- `src/main/resources/db/migration/V13__tool_schema.sql`：三张工具表。
- `src/test/java/com/example/aichat/tool/`：领域、执行器、服务、Mapper 和配置测试。

### 修改现有文件

- `pom.xml`：注册 `ai-platform-tool` 模块和 MCP SDK 版本。
- `ai-platform-boot/pom.xml`：加入 `ai-platform-tool` 依赖。
- `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`：加入稳定的工具错误码。
- `ai-platform-admin/pom.xml`：加入 `ai-platform-tool` 直接依赖。
- `ai-platform-admin/src/main/resources/db/migration/V14__tool_permission.sql`：注册工具权限。
- `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/`：新增工具、MCP Server、调用日志控制器。
- `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/`：新增管理控制器测试。
- `ai-platform-boot/src/main/resources/application.yml`：加入工具执行限制配置。
- `docs/backend-development-guide.md`、`docs/frontend-api-integration.md`：补充模块、配置、接口和错误码说明。

---

### Task 1: 建立 Maven 模块和运行配置

**Files:**
- Modify: `pom.xml:10-18,20-30`
- Create: `ai-platform-tool/pom.xml`
- Modify: `ai-platform-boot/pom.xml:12-47`
- Modify: `ai-platform-admin/pom.xml:11-36`
- Modify: `ai-platform-boot/src/main/resources/application.yml:4-9`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/config/ToolProperties.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/config/ToolConfiguration.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/config/ToolConfigurationTest.java`

- [ ] **Step 1: 写模块启动失败测试**

创建 `ToolConfigurationTest`，用 `ApplicationContextRunner` 加载 `ToolConfiguration` 和 `ToolProperties`，断言默认限制值为：最大 Header 数 32、最大请求体 1 MiB、最大响应体 1 MiB、默认连接超时 10 秒、默认读取超时 60 秒、最大超时 600 秒、禁止私网访问。

```java
new ApplicationContextRunner()
        .withUserConfiguration(ToolConfiguration.class)
        .withPropertyValues("tool.max-header-count=32")
        .run(context -> {
            assertThat(context).hasSingleBean(ToolProperties.class);
            assertThat(context.getBean(ToolProperties.class).getMaxHeaderCount()).isEqualTo(32);
        });
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolConfigurationTest test
```

预期：模块尚不存在，Maven 或测试类编译失败。

- [ ] **Step 3: 注册模块和依赖**

在根 `pom.xml` 增加：

```xml
<mcp.version>0.18.3</mcp.version>
```

在 `<modules>` 中将 `ai-platform-tool` 放在 `ai-platform-ai` 之后、`ai-platform-chat` 之前。创建模块 POM，包含以下依赖：

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-common</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-ai</artifactId>
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
    <groupId>io.modelcontextprotocol.sdk</groupId>
    <artifactId>mcp</artifactId>
    <version>${mcp.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>test</scope>
</dependency>
```

把 `ai-platform-tool` 加入 boot POM，并把 tool 作为 admin 的直接依赖。新增的 tool POM 不依赖 admin，保持运行时层与管理层单向依赖。

- [ ] **Step 4: 实现配置属性和配置 Bean**

`ToolProperties` 使用 `@ConfigurationProperties(prefix = "tool")`，提供以下属性和默认值：

```java
private int maxHeaderCount = 32;
private int maxBodyBytes = 1_048_576;
private int maxResponseBytes = 1_048_576;
private int defaultConnectTimeout = 10;
private int defaultReadTimeout = 60;
private int maxTimeout = 600;
private boolean allowPrivateNetwork = false;
```

`ToolConfiguration` 使用 `@EnableConfigurationProperties(ToolProperties.class)`，提供 `DefaultJsonSchemaValidator` Bean。Task 3 再向该配置增加依赖 `AiCredentialCipher` 的 `ToolCredentialHeaders` Bean，不复制 AES 实现。

在 `application.yml` 增加：

```yaml
tool:
  max-header-count: ${TOOL_MAX_HEADER_COUNT:32}
  max-body-bytes: ${TOOL_MAX_BODY_BYTES:1048576}
  max-response-bytes: ${TOOL_MAX_RESPONSE_BYTES:1048576}
  default-connect-timeout: ${TOOL_DEFAULT_CONNECT_TIMEOUT:10}
  default-read-timeout: ${TOOL_DEFAULT_READ_TIMEOUT:60}
  max-timeout: ${TOOL_MAX_TIMEOUT:600}
  allow-private-network: ${TOOL_ALLOW_PRIVATE_NETWORK:false}
```

- [ ] **Step 5: 运行测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolConfigurationTest test
```

预期：`ToolConfigurationTest` PASS，模块编译成功。

- [ ] **Step 6: 提交模块骨架**

```bash
git add pom.xml ai-platform-tool/pom.xml ai-platform-boot/pom.xml ai-platform-admin/pom.xml ai-platform-boot/src/main/resources/application.yml
git commit -m "feat: add tool module skeleton"
```

---

### Task 2: 创建数据库迁移、实体、枚举和 Mapper

**Files:**
- Create: `ai-platform-tool/src/main/resources/db/migration/V13__tool_schema.sql`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolType.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolSource.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolAvailability.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolAuthType.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolHttpMethod.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolInvocationStatus.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/enums/ToolDiscoveryStatus.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/entity/ToolDefinition.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/entity/ToolMcpServer.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/entity/ToolInvocationLog.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/mapper/ToolDefinitionMapper.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/mapper/ToolMcpServerMapper.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/mapper/ToolInvocationLogMapper.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/mapper/ToolMapperIntegrationTest.java`

- [ ] **Step 1: 写迁移集成测试**

使用 `@Testcontainers(disabledWithoutDocker = true)` 和 `MySQLContainer<>("mysql:8.4")`。测试先通过 Flyway 执行 `classpath:db/migration/V13__tool_schema.sql`，然后用 MyBatis-Plus Mapper 插入并读取一条 MCP Server、一条 HTTP Tool 和一条调用日志，断言 JSON 字符串、枚举字符串、`deleted` 默认值和唯一索引行为。

测试方法必须包含：

```java
@Test
void storesToolConfigurationsAndInvocationLogs() { }

@Test
void logicDeletedRowsAreNotReturnedByBaseMapperQueries() { }

@Test
void duplicateCodesAreRejectedByUniqueIndexes() { }
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolMapperIntegrationTest test
```

预期：表、实体和 Mapper 尚不存在，测试编译或数据库初始化失败。

- [ ] **Step 3: 写 `V13__tool_schema.sql`**

创建三张表，字段必须与以下契约一致：

```sql
create table if not exists tool_mcp_server (
    id bigint not null auto_increment primary key,
    server_code varchar(100) not null,
    server_name varchar(100) not null,
    endpoint_url varchar(500) not null,
    auth_type varchar(32) not null default 'NONE',
    auth_header_name varchar(100) null,
    credential_ciphertext varchar(4096) null,
    credential_version int not null default 1,
    secret_headers_ciphertext text null,
    connect_timeout int not null default 10,
    read_timeout int not null default 60,
    status tinyint not null default 1,
    deleted tinyint not null default 0,
    last_discovery_time datetime null,
    last_discovery_status varchar(32) null,
    last_discovery_error varchar(1000) null,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    unique key uk_tool_mcp_server_code (server_code),
    key idx_tool_mcp_server_status (status, deleted)
);

create table if not exists tool_definition (
    id bigint not null auto_increment primary key,
    tool_code varchar(100) not null,
    tool_name varchar(100) not null,
    description varchar(1000) null,
    tool_type varchar(32) not null,
    source varchar(32) not null,
    status tinyint not null default 0,
    availability varchar(32) not null default 'AVAILABLE',
    mcp_server_id bigint null,
    remote_tool_name varchar(200) null,
    http_config json null,
    auth_type varchar(32) not null default 'NONE',
    auth_header_name varchar(100) null,
    credential_ciphertext varchar(4096) null,
    credential_version int not null default 1,
    secret_headers_ciphertext text null,
    input_schema json null,
    deleted tinyint not null default 0,
    create_time datetime not null default current_timestamp,
    update_time datetime not null default current_timestamp on update current_timestamp,
    unique key uk_tool_definition_code (tool_code),
    unique key uk_tool_definition_mcp_remote (mcp_server_id, remote_tool_name),
    key idx_tool_definition_status (tool_type, status, availability, deleted),
    key idx_tool_definition_server (mcp_server_id, deleted)
);

create table if not exists tool_invocation_log (
    id bigint not null auto_increment primary key,
    request_id varchar(64) not null,
    tool_id bigint null,
    tool_code varchar(100) not null,
    user_id bigint null,
    tool_type varchar(32) not null,
    status varchar(32) not null,
    duration_ms bigint null,
    error_code varchar(100) null,
    error_message varchar(1000) null,
    start_time datetime not null,
    end_time datetime null,
    create_time datetime not null default current_timestamp,
    key idx_tool_invocation_request (request_id),
    key idx_tool_invocation_tool_time (tool_id, start_time),
    key idx_tool_invocation_user_time (user_id, start_time),
    key idx_tool_invocation_status_time (status, start_time)
);
```

不创建数据库外键，沿用现有业务表的 ID 关联方式；`@TableLogic` 负责 `deleted` 过滤。

- [ ] **Step 4: 实现实体和 Mapper**

三个实体使用 `@TableName`、自增 `@TableId` 和 `@TableLogic`，JSON 列在实体中以 `String` 保存，由服务层负责 Jackson 校验/序列化。枚举字段使用 `String` 形式持久化，Mapper 统一继承 `BaseMapper<T>`：

```java
@Mapper
public interface ToolDefinitionMapper extends BaseMapper<ToolDefinition> { }

@Mapper
public interface ToolMcpServerMapper extends BaseMapper<ToolMcpServer> { }

@Mapper
public interface ToolInvocationLogMapper extends BaseMapper<ToolInvocationLog> { }
```

实体至少提供 MyBatis-Plus 需要的标准 getter/setter，并将字段命名为 `toolCode`、`mcpServerId`、`inputSchema` 等以匹配下划线转驼峰配置；两个带凭证的实体都包含 `credentialVersion`，首次保存为 1，每次替换明文凭证时递增。

- [ ] **Step 5: 运行集成测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolMapperIntegrationTest test
```

预期：Docker 可用时三个 Mapper 集成测试 PASS；Docker 不可用时测试按 `disabledWithoutDocker = true` 跳过而不失败。

- [ ] **Step 6: 提交持久化基础**

```bash
git add ai-platform-tool/src/main/resources/db/migration/V13__tool_schema.sql ai-platform-tool/src/main/java/com/example/aichat/tool
git add ai-platform-tool/src/test/java/com/example/aichat/tool/mapper/ToolMapperIntegrationTest.java
git commit -m "feat: add tool persistence schema"
```

---

### Task 3: 定义错误码、DTO、配置序列化和安全校验

**Files:**
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java:5-33`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/execution/ToolExecuteRequest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/execution/ToolExecutionResult.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/HttpToolConfig.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolUpsertRequest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolStatusRequest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolManagementView.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/McpServerUpsertRequest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/McpServerStatusRequest.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/McpServerManagementView.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolInvocationQuery.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolInvocationView.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolDiscoveryResult.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/ToolConfigCodec.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/TemplateValueResolver.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/UrlSafetyValidator.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/ToolCredentialHeaders.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/validation/TemplateValueResolverTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/validation/UrlSafetyValidatorTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/validation/ToolConfigCodecTest.java`

- [ ] **Step 1: 写 DTO 和安全校验失败测试**

先写以下测试：

```java
@Test
void resolvesTopLevelAndNestedTemplateValuesWithoutExecutingExpressions() { }

@Test
void rejectsMissingTemplateValues() { }

@Test
void rejectsLoopbackPrivateAndMetadataAddresses() { }

@Test
void encryptsSecretHeadersAndNeverReturnsPlaintextCiphertextView() { }

@Test
void rejectsHttpConfigWithUnsupportedMethodOrMissingUrl() { }
```

模板只支持 `{{name}}` 和 `{{customer.id}}` 形式的 Map 路径；表达式中出现 `(`、`)`、`T(`、`;` 或 `$` 直接返回 `TOOL_ARGUMENT_INVALID`。URL 校验测试使用 `http://127.0.0.1`、`http://localhost`、`http://169.254.169.254` 和 `http://10.0.0.1`，并验证公网示例 `https://example.com/api` 在 `allowPrivateNetwork=false` 时通过 URI 语法校验。

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='*ValidationTest,*CodecTest' test
```

预期：DTO/校验器尚不存在，测试编译失败。

- [ ] **Step 3: 增加稳定工具错误码**

在 `ErrorCode` 中增加：

```java
TOOL_NOT_FOUND(HttpStatus.NOT_FOUND, "TOOL_NOT_FOUND", "Tool not found"),
TOOL_DISABLED(HttpStatus.CONFLICT, "TOOL_DISABLED", "Tool is disabled"),
TOOL_STALE(HttpStatus.CONFLICT, "TOOL_STALE", "Tool discovery snapshot is stale"),
TOOL_ARGUMENT_INVALID(HttpStatus.BAD_REQUEST, "TOOL_ARGUMENT_INVALID", "Tool arguments are invalid"),
TOOL_CONFIG_INVALID(HttpStatus.BAD_REQUEST, "TOOL_CONFIG_INVALID", "Tool configuration is invalid"),
TOOL_CONNECT_TIMEOUT(HttpStatus.BAD_GATEWAY, "TOOL_CONNECT_TIMEOUT", "Tool connection timed out"),
TOOL_READ_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "TOOL_READ_TIMEOUT", "Tool response timed out"),
TOOL_REMOTE_ERROR(HttpStatus.BAD_GATEWAY, "TOOL_REMOTE_ERROR", "Tool remote request failed"),
TOOL_DISCOVERY_FAILED(HttpStatus.BAD_GATEWAY, "TOOL_DISCOVERY_FAILED", "MCP tool discovery failed"),
TOOL_EXECUTION_FAILED(HttpStatus.BAD_GATEWAY, "TOOL_EXECUTION_FAILED", "Tool execution failed")
```

- [ ] **Step 4: 实现请求/响应 DTO**

使用 record 和 Jakarta Validation：

```java
public record ToolExecuteRequest(
        @NotBlank @Size(max = 100) String toolCode,
        @NotNull Map<String, Object> arguments,
        @Size(max = 64) String requestId) { }

public record HttpToolConfig(
        @NotBlank @Size(max = 500) String url,
        @NotNull ToolHttpMethod method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        JsonNode bodyTemplate) { }
```

`ToolUpsertRequest` 包含 `toolCode`、`toolName`、`description`、`toolType`、`httpConfig`、`authType`、`authHeaderName`、`credential`、`secretHeaders` 和 `status`；`McpServerUpsertRequest` 包含 `serverCode`、`serverName`、`endpointUrl`、相同认证字段、连接/读取超时和 `status`。认证明文只存在于请求 DTO，不进入实体或响应 DTO。

`ToolManagementView` 和 `McpServerManagementView` 只包含 `hasCredential`、`credentialVersion` 等状态信息；`ToolExecutionResult` 包含 `requestId`、`toolCode`、`toolType`、`success`、`statusCode`、`data` 和 `durationMs`。

- [ ] **Step 5: 实现 JSON 编解码、模板和 URL 校验**

`ToolConfigCodec` 使用 `ObjectMapper` 将 `HttpToolConfig` 编解码为实体的 `httpConfig` JSON，并拒绝空 URL、缺少方法、超过 Header/Body 限制和非 HTTP/HTTPS scheme。

`TemplateValueResolver` 递归处理 JSON Object、Array 和文本节点；文本完全等于单个占位符时保留参数原始类型，混合文本时转成字符串；缺少参数抛 `BusinessException(TOOL_ARGUMENT_INVALID)`。

`UrlSafetyValidator` 接收 `ToolProperties` 和可替换的 DNS 解析函数，解析 URL host 的全部地址，默认拒绝 loopback、link-local、site-local、私网和 IPv6 本地地址；只在 `allowPrivateNetwork=true` 时放行私网地址，仍拒绝无 scheme、用户名密码、片段和非 HTTP/HTTPS URI。

`ToolCredentialHeaders` 使用 `AiCredentialCipher` 加密/解密 JSON Header Map：密文只写入实体的 `secretHeadersCiphertext`，管理视图只返回 `hasCredential`。同时修改 `ToolConfiguration`，注入 `AiCredentialCipher` 并注册 `ToolCredentialHeaders` Bean。

- [ ] **Step 6: 运行测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='*ValidationTest,*CodecTest' test
```

预期：所有 DTO/安全校验测试 PASS。

- [ ] **Step 7: 提交 DTO 和安全边界**

```bash
git add ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java ai-platform-tool/src/main/java/com/example/aichat/tool/dto ai-platform-tool/src/main/java/com/example/aichat/tool/validation
git add ai-platform-tool/src/test/java/com/example/aichat/tool/validation
git commit -m "feat: define tool contracts and security validation"
```

---

### Task 4: 实现工具和 MCP Server 管理服务

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/api/ToolManagementService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/api/McpServerManagementService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolManagementServiceImpl.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/McpServerManagementServiceImpl.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/DiscoveredTool.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/ToolManagementServiceImplTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/McpServerManagementServiceImplTest.java`

- [ ] **Step 1: 写管理服务失败测试**

`ToolManagementServiceImplTest` 使用 Mockito Mapper 和真实 `AiCredentialCipher`，覆盖：

```java
@Test
void createsHttpToolWithEncryptedCredentialAndNeverExposesIt() { }

@Test
void rejectsDuplicateToolCode() { }

@Test
void updateWithoutCredentialPreservesExistingCiphertext() { }

@Test
void discoveredMcpToolCannotBeOverwrittenAsHttpConfiguration() { }

@Test
void deleteMarksToolDeletedAndKeepsInvocationRelation() { }

@Test
void staleOrDisabledToolCannotBeRequiredForExecution() { }
```

`McpServerManagementServiceImplTest` 覆盖：

```java
@Test
void createsServerWithEncryptedCredential() { }

@Test
void updateWithoutCredentialPreservesExistingCredential() { }

@Test
void rejectsDeleteWhenNonDeletedToolsStillReferenceServer() { }

@Test
void disabledServerIsNotReturnedAsExecutableServer() { }
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='*ManagementServiceImplTest' test
```

预期：服务接口和实现尚不存在，测试编译失败。

- [ ] **Step 3: 定义服务接口**

接口签名固定为：

```java
public interface ToolManagementService {
    List<ToolManagementView> list();
    ToolManagementView getRequired(Long id);
    ToolDefinition getByCodeRequired(String toolCode);
    ToolManagementView create(ToolUpsertRequest request);
    ToolManagementView update(Long id, ToolUpsertRequest request);
    void delete(Long id);
    void updateStatus(Long id, int status);
    ToolDefinition requireExecutable(String toolCode);
}

public interface McpServerManagementService {
    List<McpServerManagementView> list();
    McpServerManagementView getRequired(Long id);
    ToolMcpServer getEnabledRequired(Long id);
    McpServerManagementView create(McpServerUpsertRequest request);
    McpServerManagementView update(Long id, McpServerUpsertRequest request);
    void delete(Long id);
    void updateStatus(Long id, int status);
    void recordDiscoverySuccess(Long id, int count);
    void recordDiscoveryFailure(Long id, String errorMessage);
}
```

创建 `service/DiscoveredTool.java`，内容为不依赖 MCP SDK 的 record：

```java
public record DiscoveredTool(
        String remoteToolName,
        String name,
        String description,
        JsonNode inputSchema) { }
```

它只存在服务层，不能把 MCP SDK 类型暴露给管理服务接口。

- [ ] **Step 4: 实现 HTTP Tool 管理**

`ToolManagementServiceImpl` 使用 `LambdaQueryWrapper` 排除逻辑删除记录并按 `updateTime desc, id desc` 排序。创建/更新时：

1. 校验 `toolCode` 全局唯一；
2. 只允许 `toolType=HTTP` 的管理请求创建手工工具；
3. 编解码 `httpConfig` 并执行 URL/方法/Header/Body 限制；
4. credential 或 secret header 非空时使用 `AiCredentialCipher` 加密，否则更新保留原密文；
5. 新工具默认 `source=MANUAL`、`availability=AVAILABLE`；
6. 状态只接受 0/1；
7. 响应映射为不含密文的 `ToolManagementView`。

删除调用 `mapper.deleteById(id)`，依赖 `@TableLogic` 设置 `deleted=1`；执行查询必须同时过滤逻辑删除。

- [ ] **Step 5: 实现 MCP Server 管理**

`McpServerManagementServiceImpl` 对 `serverCode` 做唯一性校验，验证 endpoint URL 和认证字段，默认连接/读取超时分别为 10/60 秒并限制在 `1..ToolProperties.maxTimeout`。更新时明文凭证为空则保留原值；删除前查询未删除的关联工具，存在关联时抛 `RESOURCE_CONFLICT`，否则逻辑删除。

- [ ] **Step 6: 实现状态和执行前校验**

`requireExecutable(toolCode)` 必须按以下顺序校验：工具不存在或已删除抛 `TOOL_NOT_FOUND`；工具 `status != 1` 抛 `TOOL_DISABLED`；工具 `availability != AVAILABLE` 抛 `TOOL_STALE`；MCP 工具关联 Server 不存在、删除或禁用时抛 `TOOL_DISABLED`。

- [ ] **Step 7: 运行管理服务测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='*ManagementServiceImplTest' test
```

预期：管理服务测试 PASS，且测试断言中不存在明文凭证出现在响应视图。

- [ ] **Step 8: 提交管理服务**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/api ai-platform-tool/src/main/java/com/example/aichat/tool/service
git add ai-platform-tool/src/test/java/com/example/aichat/tool/service
git commit -m "feat: add tool and MCP server management"
```

---

### Task 5: 实现 HTTP Tool 执行器

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/executor/ToolExecutor.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/executor/ToolExecutionContext.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/executor/HttpToolExecutor.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/executor/ToolRemoteException.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/executor/HttpToolExecutorTest.java`

- [ ] **Step 1: 写 HTTP 执行器失败测试**

使用现有项目中的 JDK `HttpServer` 测试方式，覆盖：

```java
@Test
void sendsResolvedQueryBodyAndAuthenticationHeaders() { }

@Test
void returnsJsonResponseWithStatusAndDuration() { }

@Test
void rejectsMissingTemplateArgumentBeforeSendingRequest() { }

@Test
void mapsNon2xxResponseToToolRemoteErrorWithoutReturningSecret() { }

@Test
void mapsConnectAndReadTimeoutsToStableErrorCodes() { }

@Test
void rejectsPrivateTargetBeforeOpeningSocket() { }
```

测试 Server 记录请求方法、URI、Header 和 Body；断言认证 Header 存在，调用参数不能覆盖认证 Header，Body 中只出现替换后的 JSON 值。

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=HttpToolExecutorTest test
```

预期：执行器接口和实现尚不存在，测试编译失败。

- [ ] **Step 3: 定义执行器契约**

```java
public interface ToolExecutor {
    ToolType supportedType();
    ToolExecutionResult execute(ToolExecutionContext context);
}

public record ToolExecutionContext(
        ToolDefinition tool,
        ToolMcpServer mcpServer,
        Map<String, Object> arguments,
        String requestId) { }
```

- [ ] **Step 4: 实现请求构造和超时**

`HttpToolExecutor` 使用 `ToolConfigCodec` 读取 `httpConfig`，使用 `TemplateValueResolver` 解析 Query 和 Body，使用 `ToolCredentialHeaders` 解密认证 Header。每次执行创建配置了连接/读取超时的 `SimpleClientHttpRequestFactory` 和 `RestClient`；不允许调用参数修改 URL、方法或认证 Header。

请求构造顺序固定为：

1. URL scheme/SSRF 校验；
2. Query 参数模板解析；
3. Header 数量和名称校验；
4. 普通 Header 写入；
5. 解密后的认证 Header 最后写入；
6. JSON Body 模板解析并限制字节数；
7. 通过 `RestClient.exchange` 读取受 `maxResponseBytes` 限制的响应体。

响应 `Content-Type` 为 JSON 时用 `ObjectMapper.readTree` 转成 `JsonNode`，其他类型转为 UTF-8 字符串。2xx 为成功，非 2xx 抛 `ToolRemoteException(TOOL_REMOTE_ERROR)`，错误信息只包含状态码和截断后的安全文本。

- [ ] **Step 5: 实现异常映射**

连接异常映射 `TOOL_CONNECT_TIMEOUT`，读取超时映射 `TOOL_READ_TIMEOUT`，JSON/模板/URL 校验映射 `TOOL_ARGUMENT_INVALID` 或 `TOOL_CONFIG_INVALID`。不得把 URI 中的 UserInfo、认证 Header 或完整响应体放入异常消息。

- [ ] **Step 6: 运行测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=HttpToolExecutorTest test
```

预期：所有 HTTP 执行器测试 PASS。

- [ ] **Step 7: 提交 HTTP 执行器**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/executor ai-platform-tool/src/test/java/com/example/aichat/tool/executor
git commit -m "feat: execute HTTP tools"
```

---

### Task 6: 实现 MCP Streamable HTTP 客户端、发现和调用

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/mcp/McpClientFactory.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/mcp/McpClientFactoryImpl.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolDiscoveryServiceImpl.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/executor/McpToolExecutor.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/api/ToolDiscoveryService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/ToolDiscoveryPersistenceService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImpl.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/ToolDiscoveryServiceImplTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/executor/McpToolExecutorTest.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/mcp/McpClientHttpIntegrationTest.java`

- [ ] **Step 1: 写 MCP 服务失败测试**

用 Mockito mock `McpClientFactory`、`McpSyncClient` 和 MCP Schema，先覆盖：

```java
@Test
void persistsNewDiscoveredToolsDisabledByDefault() { }

@Test
void updatesExistingSnapshotAndPreservesStableToolCodeAndStatus() { }

@Test
void marksToolsMissingFromListAsStale() { }

@Test
void discoveryFailurePreservesOldSnapshotAndRecordsFailure() { }

@Test
void callsRemoteToolWithPersistedRemoteNameAndArguments() { }

@Test
void mapsMcpIsErrorResultToToolRemoteError() { }
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='ToolDiscoveryServiceImplTest,McpToolExecutorTest' test
```

预期：MCP 工厂、发现服务和执行器尚不存在，测试编译失败。

- [ ] **Step 3: 实现 MCP 客户端工厂**

定义：

```java
public interface McpClientFactory {
    McpSyncClient open(ToolMcpServer server);
}
```

实现使用官方 SDK：

```java
HttpClientStreamableHttpTransport transport =
        HttpClientStreamableHttpTransport.builder(server.getEndpointUrl())
                .connectTimeout(Duration.ofSeconds(server.getConnectTimeout()))
                .customizeRequest(builder -> addAuthenticationHeaders(builder, server))
                .build();

return McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(server.getReadTimeout()))
        .initializationTimeout(Duration.ofSeconds(server.getReadTimeout()))
        .clientInfo(new McpSchema.Implementation("ai-platform-tool", "0.0.1"))
        .jsonSchemaValidator(new DefaultJsonSchemaValidator(objectMapper))
        .enableCallToolSchemaCaching(true)
        .build();
```

`addAuthenticationHeaders` 解密 Bearer/API Key 和自定义 Header；认证值只在内存中存在，异常和日志不打印。每个调用方用 `try (McpSyncClient client = factory.open(server))` 管理生命周期。

```java
public interface ToolDiscoveryService {
    ToolDiscoveryResult discover(Long serverId);
}

public interface ToolDiscoveryPersistenceService {
    ToolDiscoveryResult persist(Long serverId, List<DiscoveredTool> tools);
}
```

- [ ] **Step 4: 实现发现服务**

`ToolDiscoveryService.discover(Long serverId)` 先读取启用 Server，打开客户端并调用 `initialize()`；循环调用 `listTools(cursor)` 直到 `nextCursor` 为空。将 SDK `McpSchema.Tool` 转成内部 `DiscoveredTool`，用 `ObjectMapper.valueToTree(tool.inputSchema())` 保存 Schema JSON。

网络调用完成后调用独立的 `ToolDiscoveryPersistenceService.persist(serverId, discoveredTools)`，该方法使用 `@Transactional`：

1. 按 `(serverId, remoteToolName)` 查询已有记录；
2. 已有记录更新名称、描述、Schema 和 `availability=AVAILABLE`；
3. 新记录生成稳定 `toolCode`、`source=DISCOVERED`、`status=0`；
4. 本次未出现的关联记录更新为 `availability=STALE`；
5. 不更新已有记录的 `status`；
6. 返回新增、更新、stale 数量。

成功后 `recordDiscoverySuccess`，失败捕获外部异常、调用 `recordDiscoveryFailure` 并抛 `TOOL_DISCOVERY_FAILED`；失败路径不得调用快照 upsert。

- [ ] **Step 5: 实现 MCP Tool 执行器**

`McpToolExecutor` 从上下文取得 MCP Server，使用工厂打开客户端，调用 `initialize()` 和：

```java
McpSchema.CallToolResult result = client.callTool(
        new McpSchema.CallToolRequest(
                context.tool().getRemoteToolName(),
                context.arguments()));
```

将 `structuredContent` 优先作为 `data`，否则将 `content` 转成 JSON 数组/文本；`isError=true` 映射 `TOOL_REMOTE_ERROR`。MCP 客户端异常按连接、超时和远端错误分类。服务层已经完成 Server/Tool 启用和 stale 校验，执行器不绕过这些条件。

- [ ] **Step 6: 建立真实 Streamable HTTP mock 测试**

`McpClientHttpIntegrationTest` 使用 JDK `HttpServer` 实现最小 MCP endpoint，维护 `Mcp-Session-Id`，处理 `initialize`、`notifications/initialized`、`tools/list` 和 `tools/call`，验证：

- 请求包含配置的 Bearer Header；
- `tools/list` 的分页 cursor 能被继续请求；
- `tools/call` 收到正确远端工具名和 arguments；
- 客户端在测试结束关闭会话。

测试 Server 只返回固定 JSON-RPC fixture，不连接外部网络。

- [ ] **Step 7: 运行 MCP 测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest='ToolDiscoveryServiceImplTest,McpToolExecutorTest,McpClientHttpIntegrationTest' test
```

预期：MCP 单元和本地协议集成测试 PASS。

- [ ] **Step 8: 提交 MCP 能力**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/mcp ai-platform-tool/src/main/java/com/example/aichat/tool/service ai-platform-tool/src/main/java/com/example/aichat/tool/executor/McpToolExecutor.java
git add ai-platform-tool/src/test/java/com/example/aichat/tool/service ai-platform-tool/src/test/java/com/example/aichat/tool/executor ai-platform-tool/src/test/java/com/example/aichat/tool/mcp
git commit -m "feat: discover and execute MCP tools"
```

---

### Task 7: 实现统一执行服务和基础日志

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/api/ToolExecutionService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/api/ToolInvocationLogService.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImpl.java`
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolInvocationLogServiceImpl.java`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/ToolExecutionServiceImplTest.java`

- [ ] **Step 1: 写统一执行失败测试**

使用两个 mock `ToolExecutor` 和 mock Mapper/日志服务，覆盖：

```java
@Test
void routesHttpToolByToolCodeAndLogsSuccessfulCall() { }

@Test
void routesMcpToolOnlyWhenServerIsEnabled() { }

@Test
void rejectsDisabledAndStaleToolsBeforeCallingExecutor() { }

@Test
void generatesRequestIdWhenRequestDoesNotContainOne() { }

@Test
void recordsFailureAndRethrowsStableBusinessException() { }

@Test
void logWriteFailureDoesNotReplaceOriginalExecutionResult() { }
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionServiceImplTest test
```

预期：统一执行服务尚不存在，测试编译失败。

- [ ] **Step 3: 定义统一服务接口和日志服务接口**

```java
public interface ToolExecutionService {
    ToolExecutionResult execute(ToolExecuteRequest request, Long userId);
}

public interface ToolInvocationLogService {
    ToolInvocationLog start(String requestId, ToolDefinition tool, Long userId);
    void success(Long logId, long durationMs);
    void failure(Long logId, long durationMs, ErrorCode errorCode, String message);
    PageResult<ToolInvocationView> page(ToolInvocationQuery query);
}
```

`ToolExecutor` 列表由 Spring 注入，启动时按 `supportedType()` 建立不可变 Map；重复类型 Bean 直接抛配置异常，避免路由不确定。

- [ ] **Step 4: 实现执行顺序和日志更新**

`ToolExecutionServiceImpl.execute` 的顺序固定为：

1. 规范化或生成 `requestId`；
2. `ToolManagementService.requireExecutable(toolCode)`；
3. 对 MCP 工具加载启用 Server；
4. 创建开始日志，保存 `toolId`、`toolCode`、`toolType`、`userId` 和开始时间；
5. 用 `System.nanoTime()` 计时并调用对应 `ToolExecutor`；
6. 成功时更新日志为 `SUCCESS`；
7. `BusinessException` 按其 ErrorCode 更新失败日志后原样抛出；
8. 其他异常更新为 `TOOL_EXECUTION_FAILED` 后抛业务异常；
9. 日志更新失败只写应用 logger，不覆盖执行结果或原始异常。

错误日志消息调用 `sanitizeErrorMessage`：截断到 1000 字符并移除 `Authorization`、`X-Api-Key` 和 `Bearer` 值。

- [ ] **Step 5: 实现日志分页查询**

使用 MyBatis-Plus `Page<ToolInvocationLog>` 和 `LambdaQueryWrapper`，过滤 `toolId`/`status`，按 `startTime desc, id desc` 排序，`page` 归一化为至少 1，`size` 限制为 1–100。响应只映射基础审计字段。

- [ ] **Step 6: 运行统一执行测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionServiceImplTest test
```

预期：统一执行和日志测试 PASS。

- [ ] **Step 7: 提交统一执行链路**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/api ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImpl.java ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolInvocationLogServiceImpl.java
git add ai-platform-tool/src/test/java/com/example/aichat/tool/service/ToolExecutionServiceImplTest.java
git commit -m "feat: add unified tool execution logging"
```

---

### Task 8: 暴露运行时和后台管理 REST API

**Files:**
- Create: `ai-platform-tool/src/main/java/com/example/aichat/tool/controller/ToolExecutionController.java`
- Create: `ai-platform-tool/src/test/java/com/example/aichat/tool/controller/ToolExecutionControllerTest.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolController.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolMcpServerController.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolInvocationController.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/ToolControllerTest.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/ToolMcpServerControllerTest.java`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/controller/ToolInvocationControllerTest.java`

- [ ] **Step 1: 写控制器失败测试**

运行时控制器测试使用 `MockMvcBuilders.standaloneSetup(new ToolExecutionController(service, currentUserProvider))`，验证：

```java
@Test
void executesToolForCurrentUser() { }

@Test
void rejectsInvalidExecutionRequest() { }
```

后台控制器测试使用现有 `AdminFileControllerTest` 风格，分别验证以下路径、服务调用和 JSON 字段：

```java
GET    /api/admin/tools
GET    /api/admin/tools/{id}
POST   /api/admin/tools
PUT    /api/admin/tools/{id}
DELETE /api/admin/tools/{id}
PATCH  /api/admin/tools/{id}/status

GET    /api/admin/tool/mcp-servers
GET    /api/admin/tool/mcp-servers/{id}
POST   /api/admin/tool/mcp-servers
PUT    /api/admin/tool/mcp-servers/{id}
DELETE /api/admin/tool/mcp-servers/{id}
PATCH  /api/admin/tool/mcp-servers/{id}/status
POST   /api/admin/tool/mcp-servers/{id}/discover

GET    /api/admin/tool/invocation-logs
```

测试通过 `jsonPath("$.data.hasCredential")` 断言响应不包含 credential 明文；发现接口断言 `created/updated/stale/tools` 字段。

- [ ] **Step 2: 运行控制器测试确认失败**

运行：

```bash
mvn -pl ai-platform-tool,ai-platform-admin -am -Dtest='*Tool*ControllerTest' test
```

预期：控制器尚不存在，测试编译失败。

- [ ] **Step 3: 实现运行时执行控制器**

`ToolExecutionController` 使用：

```java
@RestController
@RequestMapping("/api/tools")
public class ToolExecutionController {
    @PostMapping("/execute")
    @SaCheckPermission("tool:execute")
    public ApiResponse<ToolExecutionResult> execute(
            @Valid @RequestBody ToolExecuteRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return ApiResponse.success(service.execute(request, user.id()));
    }
}
```

控制器不直接访问 Mapper，不接受用户 ID 参数，日志调用者来自 `CurrentUserProvider`。

- [ ] **Step 4: 实现工具管理控制器**

`ToolController` 路径为 `/api/admin/tools`，逐个方法使用 `@SaCheckPermission`：`tool:read`、`tool:create`、`tool:update`、`tool:delete`。状态接口接收 `ToolStatusRequest`，删除返回 `ApiResponse.success(null)`。

- [ ] **Step 5: 实现 MCP Server 和发现控制器**

`ToolMcpServerController` 路径为 `/api/admin/tool/mcp-servers`，管理接口使用 `tool:mcp-server:*` 权限，`POST /{id}/discover` 使用 `tool:mcp-server:discover` 并调用 `ToolDiscoveryService.discover(id)`。

- [ ] **Step 6: 实现日志查询控制器**

`ToolInvocationController` 路径为 `/api/admin/tool/invocation-logs`，使用 `@ModelAttribute @Valid ToolInvocationQuery` 和 `tool:invocation:read`，返回 `ApiResponse<PageResult<ToolInvocationView>>`。

- [ ] **Step 7: 运行控制器测试确认通过**

运行：

```bash
mvn -pl ai-platform-tool,ai-platform-admin -am -Dtest='*Tool*ControllerTest' test
```

预期：所有运行时和后台控制器测试 PASS。

- [ ] **Step 8: 提交 REST API**

```bash
git add ai-platform-tool/src/main/java/com/example/aichat/tool/controller ai-platform-tool/src/test/java/com/example/aichat/tool/controller
git add ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolController.java ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolMcpServerController.java ai-platform-admin/src/main/java/com/example/aichat/admin/controller/ToolInvocationController.java
git add ai-platform-admin/src/test/java/com/example/aichat/admin/controller/*Tool*ControllerTest.java
git commit -m "feat: expose tool management and execution APIs"
```

---

### Task 9: 注册权限并验证完整 Flyway 启动

**Files:**
- Create: `ai-platform-admin/src/main/resources/db/migration/V14__tool_permission.sql`
- Create: `ai-platform-admin/src/test/java/com/example/aichat/admin/migration/ToolPermissionMigrationTest.java`
- Create: `ai-platform-boot/src/test/java/com/example/aichat/boot/AiPlatformApplicationTest.java`

- [ ] **Step 1: 写权限迁移测试**

`ToolPermissionMigrationTest` 位于 admin 模块，使用 MySQL Testcontainer 建立最小 `sys_permission` 表后执行 `V13` 和 `V14`，查询并断言所有以下权限各出现一次且状态为 1：

```text
tool:read
tool:create
tool:update
tool:delete
tool:execute
tool:mcp-server:read
tool:mcp-server:create
tool:mcp-server:update
tool:mcp-server:delete
tool:mcp-server:discover
tool:invocation:read
```

- [ ] **Step 2: 运行测试确认失败**

运行：

```bash
mvn -pl ai-platform-admin -am -Dtest=ToolPermissionMigrationTest test
```

预期：`V14__tool_permission.sql` 不存在，权限查询失败。

- [ ] **Step 3: 创建权限迁移**

`V14__tool_permission.sql` 使用 `insert ignore`，为 11 个权限写入 `parent_id=0`、`permission_type=3`、`status=1`，排序从 300 到 310，权限编码与上一步完全一致。

- [ ] **Step 4: 验证完整应用上下文**

启动模块加载 `@SpringBootApplication(scanBasePackages = "com.example.aichat")` 的应用上下文，确认 `ToolDefinitionMapper`、`ToolMcpServerMapper`、`ToolInvocationLogMapper`、三个执行/管理服务和所有控制器均能创建，且 Flyway 能按 `V13`、`V14` 完成迁移。

- [ ] **Step 5: 运行迁移和应用测试确认通过**

运行：

```bash
mvn -pl ai-platform-admin,ai-platform-boot -am -Dtest='ToolPermissionMigrationTest,AiPlatformApplicationTest' test
```

预期：权限迁移和 Spring Bean 装配测试 PASS；无 Docker 时仅 Testcontainers 测试跳过，应用上下文测试仍需通过。

- [ ] **Step 6: 提交权限和装配验证**

```bash
git add ai-platform-admin/src/main/resources/db/migration/V14__tool_permission.sql ai-platform-admin/src/test/java/com/example/aichat/admin/migration/ToolPermissionMigrationTest.java ai-platform-boot/src/test/java/com/example/aichat/boot/AiPlatformApplicationTest.java
git commit -m "feat: register tool permissions and migration checks"
```

---

### Task 10: 更新后端/前端接口文档

**Files:**
- Modify: `docs/backend-development-guide.md`
- Modify: `docs/frontend-api-integration.md`
- Test: 文档命令检查，不新增代码测试文件

- [ ] **Step 1: 更新后端开发指南**

在模块划分表加入 `ai-platform-tool`，说明其职责为 HTTP/MCP 工具配置、发现、执行和日志；在数据表表格加入 `tool_definition`、`tool_mcp_server`、`tool_invocation_log`；在配置表加入 `TOOL_*` 环境变量；在定位索引加入 `ToolExecutionService`、`HttpToolExecutor`、`McpToolExecutor` 和 `ToolDiscoveryService`。

- [ ] **Step 2: 更新前端接口文档**

增加完整接口契约：

- `/api/admin/tools` CRUD 和状态请求体；
- `/api/admin/tool/mcp-servers` CRUD、状态和发现；
- `/api/tools/execute` 请求体和统一结果；
- `/api/admin/tool/invocation-logs` 分页字段；
- 认证字段只返回 `hasCredential`；
- 新增权限编码和工具错误码；
- 说明 MCP 新发现工具默认禁用、`STALE` 不可执行、调用参数为 JSON Object。

- [ ] **Step 3: 检查文档接口与控制器一致**

运行：

```bash
grep -nE '/api/(admin/)?(tools|tool/|tool-invocations)' docs/frontend-api-integration.md
grep -nE 'ai-platform-tool|tool_definition|tool_mcp_server|tool_invocation_log' docs/backend-development-guide.md
```

预期：文档中只出现已实现的 `/api/tools/execute`、`/api/admin/tools`、`/api/admin/tool/mcp-servers` 和 `/api/admin/tool/invocation-logs` 路径。

- [ ] **Step 4: 提交文档**

```bash
git add docs/backend-development-guide.md docs/frontend-api-integration.md
git commit -m "docs: document HTTP and MCP tool APIs"
```

---

### Task 11: 全量验证和完成前审查

**Files:**
- Verify: `pom.xml`
- Verify: `ai-platform-tool/**`
- Verify: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/Tool*.java`
- Verify: `ai-platform-admin/src/main/resources/db/migration/V14__tool_permission.sql`
- Verify: `docs/backend-development-guide.md`
- Verify: `docs/frontend-api-integration.md`

- [ ] **Step 1: 运行模块级测试**

```bash
mvn -pl ai-platform-tool,ai-platform-admin,ai-platform-boot -am test
```

预期：tool、admin、boot 及其依赖模块测试通过；Testcontainers 测试在 Docker 不可用时显示 skipped，不得出现编译错误。

- [ ] **Step 2: 运行聚合测试和打包**

```bash
mvn test
mvn -DskipTests package
```

预期：聚合测试和 Spring Boot 打包均成功，`ai-platform-boot/target` 产物包含 tool 模块类和 MCP SDK 依赖。

- [ ] **Step 3: 检查安全信息泄露**

```bash
grep -RIn --exclude-dir=target -E 'credentialCiphertext|secretHeadersCiphertext|Authorization|Bearer' ai-platform-tool/src/main/java ai-platform-admin/src/main/java
```

人工确认：

- 这些字段只在实体/内部服务之间使用；
- `ToolManagementView`、`McpServerManagementView`、`ToolInvocationView` 和异常响应不返回明文或密文；
- 日志中没有完整 arguments、响应体或认证 Header；
- URL 校验发生在保存和执行两个边界；
- MCP Tool 的 `toolCode` 调用不会让请求方指定 endpoint 或 remote tool name。

- [ ] **Step 4: 检查变更和剩余文件**

```bash
git status --short
git diff master --stat
git diff master --check
```

预期：只有本功能实现、测试和文档变更；`.DS_Store` 保持未跟踪，不加入任何提交；无 whitespace error。

- [ ] **Step 5: 按 verification skill 复核后再报告完成**

在报告实现完成前运行 `superpowers:verification-before-completion`，只根据实际测试输出汇报结果，不以编译缓存或计划内容代替验证证据。
