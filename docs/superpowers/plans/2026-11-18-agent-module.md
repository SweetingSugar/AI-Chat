# Agent 模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新增用户私有 Agent 模块，让用户管理固定模型、系统提示词、私有知识库和全局工具，并通过现有聊天 SSE 自动检索知识库和调用工具。

**Architecture:** 新增 `ai-platform-agent` 作为 Agent 配置和运行时解析模块，使用 MySQL 保存 Agent 及两类绑定关系；它依赖现有 AI、知识库和工具服务，但不依赖聊天模块。`ai-platform-chat` 增加可选 `agentId`，解析 Agent 后把固定模型、提示词、知识库 ID 和工具 code 注入现有聊天编排，继续复用 `KnowledgeRetrievalService`、`AiToolProvider` 和模型工具调用循环。

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, MySQL Flyway, PostgreSQL knowledge repository, Spring MVC, Reactor, JUnit 5, Mockito, AssertJ.

---

## File map

### 新增 Agent 模块

- **Create:** `ai-platform-agent/pom.xml`
  - 引入 common、ai、knowledge、tool、Spring Web/Validation、MyBatis-Plus 和测试依赖。
- **Create:** `ai-platform-agent/src/main/resources/db/migration/V15__agent_schema.sql`
  - 创建 `agent_definition`、`agent_knowledge_base`、`agent_tool` 三张 MySQL 表。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentDefinition.java`
  - Agent 主实体，使用 `@TableLogic` 处理逻辑删除。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentKnowledgeBase.java`
  - Agent 与知识库绑定实体。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentTool.java`
  - Agent 与全局工具绑定实体。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentDefinitionMapper.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentKnowledgeBaseMapper.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentToolMapper.java`
  - 三个 `BaseMapper` 接口。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/dto/AgentUpsertRequest.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/dto/AgentStatusRequest.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/vo/AgentVO.java`
  - 用户可见的 Agent 配置响应。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/model/AgentRuntimeConfig.java`
  - Chat 模块使用的不可变运行时配置。
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentService.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentRuntimeService.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentServiceImpl.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentRuntimeServiceImpl.java`
- **Create:** `ai-platform-agent/src/main/java/com/example/aichat/agent/controller/AgentController.java`

### 修改现有模块

- **Modify:** `pom.xml`
  - 把 `ai-platform-agent` 加入 Maven reactor。
- **Modify:** `ai-platform-chat/pom.xml`
  - 增加对 `ai-platform-agent` 的依赖。
- **Modify:** `ai-platform-boot/pom.xml`
  - 增加对 `ai-platform-agent` 的运行时依赖。
- **Modify:** `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`
  - 添加 Agent 资源和运行时错误码。
- **Modify:** `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRequestDTO.java`
  - 增加可选 `agentId`，保留旧构造函数。
- **Modify:** `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRegenerateDTO.java`
  - 增加可选 `agentId`，保留旧构造函数。
- **Modify:** `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java`
  - 增加 Agent system prompt 字段，保留旧构造函数。
- **Modify:** `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java`
  - 解析 Agent，使用 Agent 固定模型和资源。
- **Modify:** `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatStreamOrchestrator.java`
  - 在知识上下文之前注入 Agent system prompt。
- **Modify:** `docs/backend-development-guide.md`
  - 增加 Agent 模块和 API 说明。
- **Modify:** `docs/frontend-api-integration.md`
  - 增加 Agent API 和聊天 `agentId` 字段说明。

### 测试文件

- **Create:** `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`
- **Create:** `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentRuntimeServiceImplTest.java`
- **Create:** `ai-platform-agent/src/test/java/com/example/aichat/agent/controller/AgentControllerTest.java`
- **Modify:** `ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatServiceImplTest.java`
  - 若当前测试类不存在，则在相同 package 下创建该测试类。
- **Modify:** `ai-platform-chat/src/test/java/com/example/aichat/chat/application/ChatStreamOrchestratorTest.java`
  - 若当前测试类不存在，则在相同 package 下创建该测试类。

---

## Task 1: 建立模块、依赖和数据库迁移

**Files:**
- Create: `ai-platform-agent/pom.xml`
- Create: `ai-platform-agent/src/main/resources/db/migration/V15__agent_schema.sql`
- Modify: `pom.xml`
- Modify: `ai-platform-chat/pom.xml`
- Modify: `ai-platform-boot/pom.xml`

- [ ] **Step 1: 添加 Maven 模块声明。**

在根 `pom.xml` 的 `<modules>` 中加入：

```xml
<module>ai-platform-agent</module>
```

放在 `ai-platform-ai`、`ai-platform-tool`、`ai-platform-chat` 之后、`ai-platform-admin` 之前，确保 reactor 能解析 Agent 对现有模块的依赖。

- [ ] **Step 2: 创建 Agent 模块 POM。**

使用现有模块的父 POM 和 Java 17 配置，内容至少包含以下依赖：

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example.aichat</groupId>
        <artifactId>ai-platform-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>ai-platform-agent</artifactId>
    <name>AI Platform Agent</name>
    <dependencies>
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
            <groupId>com.example.aichat</groupId>
            <artifactId>ai-platform-knowledge</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.example.aichat</groupId>
            <artifactId>ai-platform-tool</artifactId>
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
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: 把 Agent 加入聊天和启动模块。**

在 `ai-platform-chat/pom.xml` 增加：

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-agent</artifactId>
    <version>${project.version}</version>
</dependency>
```

在 `ai-platform-boot/pom.xml` 增加相同依赖，使最终 Spring Boot 包扫描 Agent Controller 和 Service。

- [ ] **Step 4: 创建 MySQL 迁移。**

创建 `V15__agent_schema.sql`：

```sql
create table if not exists agent_definition (
    id bigint not null auto_increment primary key comment 'Agent 主键 ID',
    user_id bigint not null comment '所属用户 ID',
    name varchar(100) not null comment 'Agent 名称',
    description varchar(1000) null comment 'Agent 描述',
    system_prompt text not null comment 'Agent 系统提示词',
    model_id bigint not null comment '聊天模型 ID',
    status tinyint not null default 1 comment '启用状态：0 禁用，1 启用',
    deleted tinyint not null default 0 comment '逻辑删除标记：0 未删除，1 已删除',
    create_time datetime not null default current_timestamp comment '创建时间',
    update_time datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    unique key uk_agent_user_name_deleted (user_id, name, deleted),
    key idx_agent_user_status (user_id, status, deleted, update_time),
    key idx_agent_model (model_id, deleted)
) comment = '用户 Agent 配置表';

create table if not exists agent_knowledge_base (
    id bigint not null auto_increment primary key comment '绑定主键 ID',
    agent_id bigint not null comment 'Agent ID',
    knowledge_base_id bigint not null comment '知识库 ID',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_agent_knowledge_base (agent_id, knowledge_base_id),
    key idx_agent_knowledge_agent (agent_id),
    key idx_agent_knowledge_base_id (knowledge_base_id)
) comment = 'Agent 知识库绑定';

create table if not exists agent_tool (
    id bigint not null auto_increment primary key comment '绑定主键 ID',
    agent_id bigint not null comment 'Agent ID',
    tool_code varchar(100) not null comment '全局工具编码',
    create_time datetime not null default current_timestamp comment '创建时间',
    unique key uk_agent_tool (agent_id, tool_code),
    key idx_agent_tool_agent (agent_id),
    key idx_agent_tool_code (tool_code)
) comment = 'Agent 工具绑定';
```

不添加跨数据库外键；删除 Agent 时由 Service 事务删除绑定关系。

- [ ] **Step 5: 运行模块结构编译检查。**

Run: `mvn -pl ai-platform-agent -am -DskipTests compile`

Expected: 在生产类尚未创建前，Maven 应能解析模块 POM 和依赖；如果命令因缺少源码目录失败，先创建 `src/main/java/com/example/aichat/agent` 空目录后重新运行，不修改其他模块逻辑。

---

## Task 2: 添加实体、Mapper、请求模型和错误码

**Files:**
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentDefinition.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentKnowledgeBase.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/entity/AgentTool.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentDefinitionMapper.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentKnowledgeBaseMapper.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/mapper/AgentToolMapper.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/dto/AgentUpsertRequest.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/dto/AgentStatusRequest.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/vo/AgentVO.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/model/AgentRuntimeConfig.java`
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java`

- [ ] **Step 1: 添加 Agent 错误码。**

在 `ErrorCode` 的工具错误码之后加入：

```java
AGENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent not found"),
AGENT_DISABLED(HttpStatus.CONFLICT, "AGENT_DISABLED", "Agent is disabled"),
AGENT_MODEL_INVALID(HttpStatus.BAD_REQUEST, "AGENT_MODEL_INVALID", "Agent chat model is invalid"),
AGENT_KNOWLEDGE_BASE_INVALID(HttpStatus.BAD_REQUEST, "AGENT_KNOWLEDGE_BASE_INVALID",
        "Agent knowledge base is invalid"),
AGENT_TOOL_INVALID(HttpStatus.BAD_REQUEST, "AGENT_TOOL_INVALID", "Agent tool is invalid"),
```

- [ ] **Step 2: 创建主实体。**

`AgentDefinition` 按 `ToolDefinition` 的写法使用 `@TableName("agent_definition")`、`@TableId(type = IdType.AUTO)` 和 `@TableLogic`。字段及 Java 类型必须是：

```java
private Long id;
private Long userId;
private String name;
private String description;
private String systemPrompt;
private Long modelId;
private Integer status;
@TableLogic
private Integer deleted;
private LocalDateTime createTime;
private LocalDateTime updateTime;
```

提供所有 getter/setter，确保 MyBatis-Plus 能完成实体映射。

- [ ] **Step 3: 创建两个绑定实体和 BaseMapper。**

绑定实体使用 `@TableName` 和自增 ID：

```java
@TableName("agent_knowledge_base")
public class AgentKnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private Long knowledgeBaseId;
    private LocalDateTime createTime;
}
```

```java
@TableName("agent_tool")
public class AgentTool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long agentId;
    private String toolCode;
    private LocalDateTime createTime;
}
```

三个 Mapper 均继承 `BaseMapper` 并添加 `@Mapper`：

```java
@Mapper
public interface AgentDefinitionMapper extends BaseMapper<AgentDefinition> {
}
```

另外两个 Mapper 分别替换泛型为 `AgentKnowledgeBase` 和 `AgentTool`。

- [ ] **Step 4: 创建请求和响应记录。**

`AgentUpsertRequest` 使用以下字段和校验：

```java
public record AgentUpsertRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 20_000) String systemPrompt,
        @NotNull Long modelId,
        @Size(max = 10) List<@NotNull Long> knowledgeBaseIds,
        @Size(max = 20) List<@NotBlank @Size(max = 100) String> toolCodes) {
}
```

在 compact constructor 中将 null 列表转为空列表，不在记录构造器中执行数据库或权限校验。

`AgentStatusRequest`：

```java
public record AgentStatusRequest(@NotNull @Min(0) @Max(1) Integer status) {
}
```

`AgentVO` 使用以下字段：

```java
public record AgentVO(
        Long id,
        String name,
        String description,
        String systemPrompt,
        Long modelId,
        List<Long> knowledgeBaseIds,
        List<String> toolCodes,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
```

`AgentRuntimeConfig` 使用不可变记录：

```java
public record AgentRuntimeConfig(
        Long agentId,
        Long modelId,
        String systemPrompt,
        List<Long> knowledgeBaseIds,
        List<String> toolCodes) {
    public AgentRuntimeConfig {
        knowledgeBaseIds = knowledgeBaseIds == null ? List.of() : List.copyOf(knowledgeBaseIds);
        toolCodes = toolCodes == null ? List.of() : List.copyOf(toolCodes);
    }
}
```

- [ ] **Step 5: 编译类型和实体映射。**

Run: `mvn -pl ai-platform-agent -am -DskipTests compile`

Expected: Agent 模块、common 模块和所有依赖模块编译成功。

---

## Task 3: 实现 Agent 服务的保存、查询和绑定校验

**Files:**
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentService.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentServiceImpl.java`
- Create: `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentServiceImplTest.java`

- [ ] **Step 1: 先写服务层失败测试。**

测试使用 Mockito 注入 `AgentDefinitionMapper`、`AgentKnowledgeBaseMapper`、`AgentToolMapper`、`AiModelService`、`KnowledgeBaseRepository`、`ToolQueryService`。覆盖以下方法：

```java
@Test
void createsAgentWithValidatedBindings() {
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(baseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.of(activeBase(11L)));
    when(toolQueryService.listAvailable()).thenReturn(List.of(availableTool("order_query")));
    when(agentMapper.selectCount(any())).thenReturn(0L);
    when(agentMapper.insert(any(AgentDefinition.class))).thenAnswer(invocation -> {
        invocation.getArgument(0, AgentDefinition.class).setId(100L);
        return 1;
    });

    AgentVO result = service.create(7L, request(2L, List.of(11L), List.of("order_query")));

    assertThat(result.id()).isEqualTo(100L);
    verify(agentKnowledgeMapper).insert(any(AgentKnowledgeBase.class));
    verify(agentToolMapper).insert(any(AgentTool.class));
}

@Test
void rejectsBindingKnowledgeBaseOwnedByAnotherUser() {
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(baseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(7L, request(2L, List.of(11L), List.of())))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID);
    verify(agentMapper, never()).insert(any());
}

@Test
void rejectsUnavailableTool() {
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(toolQueryService.listAvailable()).thenReturn(List.of());

    assertThatThrownBy(() -> service.create(7L, request(2L, List.of(), List.of("missing"))))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_TOOL_INVALID);
}

@Test
void updateValidatesEverythingBeforeDeletingOldBindings() {
    when(agentMapper.selectOne(any())).thenReturn(existingAgent(100L, 7L));
    when(modelService.requireEnabled(3L)).thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

    assertThatThrownBy(() -> service.update(7L, 100L, request(3L, List.of(), List.of())))
            .isInstanceOf(BusinessException.class);
    verify(agentKnowledgeMapper, never()).delete(any());
    verify(agentToolMapper, never()).delete(any());
}

@Test
void rejectsDuplicateNameForSameUser() {
    when(agentMapper.selectCount(any())).thenReturn(1L);

    assertThatThrownBy(() -> service.create(7L, request(2L, List.of(), List.of())))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
}
```

测试 fixture 必须让 `AiModel.type()` 为 `AiModelType.CHAT`；对 embedding 模型增加一个测试，确认返回 `AGENT_MODEL_INVALID`。

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentServiceImplTest test`

Expected: 编译失败，因为 `AgentService` 和 `AgentServiceImpl` 尚不存在。

- [ ] **Step 3: 定义服务接口。**

`AgentService` 提供以下用户范围方法：

```java
PageResult<AgentVO> page(Long userId, int page, int size);
AgentVO get(Long userId, Long agentId);
AgentVO create(Long userId, AgentUpsertRequest request);
AgentVO update(Long userId, Long agentId, AgentUpsertRequest request);
void delete(Long userId, Long agentId);
AgentVO updateStatus(Long userId, Long agentId, AgentStatusRequest request);
```

分页大小在服务层限制为 1 到 100，页码最小为 1。

- [ ] **Step 4: 实现主实体加载和用户边界。**

在 `AgentServiceImpl` 中用 `LambdaQueryWrapper<AgentDefinition>` 的 `userId` 和 `id` 查询主实体。`@TableLogic` 会排除已删除记录；查询不到时统一抛出 `AGENT_NOT_FOUND`。所有管理方法的第一参数必须是 `userId`，不能从请求体读取 owner。

列表查询使用：

```java
new LambdaQueryWrapper<AgentDefinition>()
        .eq(AgentDefinition::getUserId, userId)
        .orderByDesc(AgentDefinition::getUpdateTime)
```

再通过 `PageResult` 返回分页数据。

- [ ] **Step 5: 实现资源标准化和保存校验。**

在写入前执行以下顺序：

1. trim name、description、systemPrompt，拒绝 trim 后为空的 name/systemPrompt；
2. `modelService.requireEnabled(request.modelId())`，并确认 `model.type == AiModelType.CHAT`；
3. 对 `knowledgeBaseIds` 去重，逐个调用 `baseRepository.findOwnedActive(userId, id)`，缺失时抛 `AGENT_KNOWLEDGE_BASE_INVALID`；
4. 对 `toolCodes` trim、去重，建立 `ToolQueryService.listAvailable()` 的 code map，缺失 code 时抛 `AGENT_TOOL_INVALID`；
5. 查询同一用户相同名称的 Agent，创建时必须为 0，更新时排除当前 Agent，否则抛 `RESOURCE_CONFLICT`；
6. 所有校验成功后才写入主实体和绑定关系。

模型校验使用：

```java
AiModel model = modelService.requireEnabled(request.modelId());
if (model.type != AiModelType.CHAT) {
    throw new BusinessException(ErrorCode.AGENT_MODEL_INVALID);
}
```

工具校验只使用 `ToolQueryService.listAvailable()` 的安全视图，不读取或复制工具认证配置。

- [ ] **Step 6: 实现创建和整体替换更新。**

`create` 和 `update` 使用 `@Transactional`。创建流程是先完整校验，再插入 `AgentDefinition`，取得自增 ID 后插入去重后的两类绑定实体。

更新流程是先加载当前用户的 Agent，再完成全部名称、模型、知识库和工具校验；校验成功后更新主实体，删除当前 Agent 的两类绑定关系，再插入新绑定。校验失败路径不得调用绑定表 delete。

- [ ] **Step 7: 实现删除、启停和响应映射。**

删除流程：

```java
@Transactional
public void delete(Long userId, Long agentId) {
    requireOwned(userId, agentId);
    agentKnowledgeBaseMapper.delete(wrapperForAgent(agentId));
    agentToolMapper.delete(wrapperForAgent(agentId));
    if (agentMapper.deleteById(agentId) == 0) {
        throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
    }
}
```

状态更新只允许 `0` 或 `1`，先加载当前用户 Agent，再更新 status。`toView` 读取两张绑定表，按照绑定记录 ID 升序返回列表，不返回其他管理字段。

- [ ] **Step 8: 运行服务层测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentServiceImplTest test`

Expected: 所有创建、更新校验、用户边界、重复名称和绑定关系测试通过。

---

## Task 4: 实现 Agent 运行时解析

**Files:**
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/AgentRuntimeService.java`
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/service/impl/AgentRuntimeServiceImpl.java`
- Create: `ai-platform-agent/src/test/java/com/example/aichat/agent/service/impl/AgentRuntimeServiceImplTest.java`

- [ ] **Step 1: 先写运行时失败测试。**

覆盖以下场景：

```java
@Test
void resolvesOwnedEnabledAgentAndRevalidatesAllBindings() {
    when(agentMapper.selectOne(any())).thenReturn(existingEnabledAgent(100L, 7L, 2L));
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(agentKnowledgeMapper.selectList(any())).thenReturn(List.of(bindingBase(11L)));
    when(baseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.of(activeBase(11L)));
    when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));
    when(toolQueryService.listAvailable()).thenReturn(List.of(availableTool("order_query")));

    AgentRuntimeConfig result = service.resolve(7L, 100L);

    assertThat(result.modelId()).isEqualTo(2L);
    assertThat(result.knowledgeBaseIds()).containsExactly(11L);
    assertThat(result.toolCodes()).containsExactly("order_query");
}

@Test
void rejectsDisabledAgentBeforeReturningRuntimeConfig() {
    when(agentMapper.selectOne(any())).thenReturn(existingDisabledAgent(100L, 7L, 2L));

    assertThatThrownBy(() -> service.resolve(7L, 100L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_DISABLED);
}

@Test
void rejectsAgentWhenBoundKnowledgeBaseIsNoLongerOwnedOrActive() {
    when(agentMapper.selectOne(any())).thenReturn(existingEnabledAgent(100L, 7L, 2L));
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(agentKnowledgeMapper.selectList(any())).thenReturn(List.of(bindingBase(11L)));
    when(baseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolve(7L, 100L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID);
}

@Test
void rejectsAgentWhenBoundToolIsNoLongerAvailable() {
    when(agentMapper.selectOne(any())).thenReturn(existingEnabledAgent(100L, 7L, 2L));
    when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
    when(agentKnowledgeMapper.selectList(any())).thenReturn(List.of());
    when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));
    when(toolQueryService.listAvailable()).thenReturn(List.of());

    assertThatThrownBy(() -> service.resolve(7L, 100L))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.AGENT_TOOL_INVALID);
}
```

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentRuntimeServiceImplTest test`

Expected: 编译失败，因为运行时接口和实现不存在。

- [ ] **Step 3: 定义并实现运行时解析接口。**

接口只暴露：

```java
public interface AgentRuntimeService {
    AgentRuntimeConfig resolve(Long userId, Long agentId);
}
```

实现按以下顺序：

1. 使用 `(userId, agentId)` 加载主实体，找不到抛 `AGENT_NOT_FOUND`；
2. status 不是 1 时抛 `AGENT_DISABLED`；
3. 用 `AiModelService.requireEnabled` 验证模型，并确认模型类型为 `CHAT`；
4. 读取 `agent_knowledge_base`，逐个用 `findOwnedActive(userId, knowledgeBaseId)` 验证；
5. 读取 `agent_tool`，建立当前 `listAvailable()` 的 code map 验证；
6. 返回 `AgentRuntimeConfig`，不修改数据库。

工具 `tool:execute` 权限不在这里校验；它继续由现有 `ToolAiToolProvider.prepare` 在真正准备工具上下文时校验，避免运行时配置解析复制权限逻辑。

- [ ] **Step 4: 运行运行时测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentRuntimeServiceImplTest test`

Expected: 所有 Agent 状态和资源二次校验测试通过。

---

## Task 5: 添加 Agent 管理 Controller

**Files:**
- Create: `ai-platform-agent/src/main/java/com/example/aichat/agent/controller/AgentController.java`
- Create: `ai-platform-agent/src/test/java/com/example/aichat/agent/controller/AgentControllerTest.java`

- [ ] **Step 1: 先写控制器失败测试。**

使用 `MockMvcBuilders.standaloneSetup` 和 Mockito，验证路径、统一响应、当前用户 ID 传递和没有 `@SaCheckPermission`：

```java
@Test
void createsAgentForCurrentUser() throws Exception {
    when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(7L, "user"));
    when(service.create(eq(7L), any(AgentUpsertRequest.class))).thenReturn(agentView(100L));

    mockMvc.perform(post("/api/agents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(100));

    verify(service).create(eq(7L), any(AgentUpsertRequest.class));
}

@Test
void exposesOnlyLoginProtectedUserScopedEndpoints() throws Exception {
    for (String name : List.of("list", "get", "create", "update", "delete", "updateStatus")) {
        Method method = findControllerMethod(name);
        assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
    }
}
```

补充 GET、PUT、DELETE、PATCH 的路径测试，验证服务方法分别接收当前用户 ID。

- [ ] **Step 2: 运行失败测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentControllerTest test`

Expected: 编译失败，因为 AgentController 不存在。

- [ ] **Step 3: 实现控制器。**

控制器使用：

```java
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private final AgentService service;
    private final CurrentUserProvider currentUserProvider;

    public AgentController(AgentService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }
}
```

方法契约：

```java
@GetMapping
ApiResponse<PageResult<AgentVO>> page(@RequestParam int page, @RequestParam int size)

@GetMapping("/{id}")
ApiResponse<AgentVO> get(@PathVariable Long id)

@PostMapping
ApiResponse<AgentVO> create(@Valid @RequestBody AgentUpsertRequest request)

@PutMapping("/{id}")
ApiResponse<AgentVO> update(@PathVariable Long id, @Valid @RequestBody AgentUpsertRequest request)

@DeleteMapping("/{id}")
ApiResponse<Void> delete(@PathVariable Long id)

@PatchMapping("/{id}/status")
ApiResponse<AgentVO> updateStatus(@PathVariable Long id, @Valid @RequestBody AgentStatusRequest request)
```

列表参数默认 page=1、size=20；服务层仍负责最终限制。所有方法都通过 `currentUserProvider.requireCurrentUser().id()` 获取用户，不添加 `@SaCheckPermission`。

- [ ] **Step 4: 运行控制器测试。**

Run: `mvn -pl ai-platform-agent -am -Dtest=AgentControllerTest test`

Expected: 所有 Agent 管理路由和用户 ID 传递测试通过。

---

## Task 6: 把 Agent 接入现有聊天链路

**Files:**
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRequestDTO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/dto/ChatRegenerateDTO.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatGenerationCommand.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatServiceImpl.java`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/application/ChatStreamOrchestrator.java`
- Create or modify: `ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatServiceImplTest.java`
- Create or modify: `ai-platform-chat/src/test/java/com/example/aichat/chat/application/ChatStreamOrchestratorTest.java`

- [ ] **Step 1: 为 DTO 和命令对象添加失败测试。**

增加测试确认旧构造函数仍能创建请求，并确认带 Agent 时请求字段可读：

```java
@Test
void keepsLegacyChatRequestConstructorCompatible() {
    ChatRequestDTO request = new ChatRequestDTO(
            "hello", List.of(), 2L, "request-1", 0.7, 0.9, 1024);

    assertThat(request.agentId()).isNull();
    assertThat(request.knowledgeBaseIds()).isEmpty();
    assertThat(request.toolCodes()).isEmpty();
}

@Test
void agentChatUsesRuntimeModelAndResourcesInsteadOfRequestOverrides() {
    ChatRequestDTO request = new ChatRequestDTO(
            "check order", List.of(), List.of(999L), 99L, "request-1",
            0.7, 0.9, 1024, List.of("unbound_tool"), 100L);
    when(agentRuntimeService.resolve(7L, 100L)).thenReturn(
            new AgentRuntimeConfig(100L, 2L, "You are support.",
                    List.of(11L), List.of("order_query")));
    when(modelGateway.resolve(2L, 5L)).thenReturn(chatModel(2L));

    service.stream(5L, request).subscribe();

    ArgumentCaptor<ChatGenerationCommand> captor = ArgumentCaptor.forClass(ChatGenerationCommand.class);
    verify(streamOrchestrator).stream(captor.capture(), eq(chatModel(2L)));
    assertThat(captor.getValue().modelId()).isEqualTo(2L);
    assertThat(captor.getValue().knowledgeBaseIds()).containsExactly(11L);
    assertThat(captor.getValue().toolCodes()).containsExactly("order_query");
    assertThat(captor.getValue().systemPrompt()).isEqualTo("You are support.");
}
```

测试 fixture 需要 mock 当前用户、session、file access、AgentRuntimeService、model gateway 和 orchestrator；不调用真实模型。

- [ ] **Step 2: 修改 Chat DTO 并保留旧构造函数。**

`ChatRequestDTO` 在现有 `toolCodes` 后增加：

```java
Long agentId
```

保留现有七参数兼容构造函数，并将 `agentId` 设为 null；增加包含所有字段的构造方式供 JSON 和新测试使用。

`ChatRegenerateDTO` 在现有字段后增加 `Long agentId`，添加一个旧字段数量的显式构造函数并把 agentId 设为 null。

- [ ] **Step 3: 修改 ChatGenerationCommand。**

在 record 中增加 `String systemPrompt`，并在 canonical constructor 中保持列表不可变。保留现有两个兼容构造函数，将 `systemPrompt` 设为 null；为 Agent 路径增加完整构造调用。

命令对象不需要保存 `agentId`，因为 Agent 只在生成开始时解析，聊天持久化继续使用现有消息表。

- [ ] **Step 4: 注入 AgentRuntimeService 并解析 Agent。**

在 `ChatServiceImpl` 增加依赖：

```java
private final AgentRuntimeService agentRuntimeService;
```

在 `stream` 中执行：

```java
AgentRuntimeConfig agent = request.agentId() == null
        ? null
        : agentRuntimeService.resolve(user.id(), request.agentId());
AiModel model = agent == null
        ? modelGateway.resolve(request.modelId(), session.defaultModelId)
        : modelGateway.resolve(agent.modelId(), session.defaultModelId);

List<Long> knowledgeBaseIds = agent == null
        ? request.knowledgeBaseIds()
        : agent.knowledgeBaseIds();
List<String> toolCodes = agent == null
        ? normalizeToolCodes(request.toolCodes())
        : agent.toolCodes();
String systemPrompt = agent == null ? null : agent.systemPrompt();
```

把 `knowledgeBaseIds`、`toolCodes` 和 `systemPrompt` 写入 `ChatGenerationCommand`。Agent 路径不读取请求中的 modelId、knowledgeBaseIds 和 toolCodes。

`regenerate` 使用同样的逻辑：有 agentId 时从 AgentRuntimeService 获取全部配置；没有 agentId 时保留现有从用户消息读取知识库和请求读取工具的逻辑。

- [ ] **Step 5: 在 ChatStreamOrchestrator 注入 system prompt。**

在现有 `messageService.context` 和知识库检索完成后构造系统消息：

```java
List<ChatMessagePair> systemMessages = new ArrayList<>();
if (command.systemPrompt() != null && !command.systemPrompt().isBlank()) {
    systemMessages.add(new ChatMessagePair(ChatMessageRole.SYSTEM,
            command.systemPrompt(), List.of()));
}
if (!knowledgeContext.sources().isEmpty()) {
    knowledgeService.saveSources(generation.userMessageId(), knowledgeContext);
    systemMessages.add(new ChatMessagePair(ChatMessageRole.SYSTEM,
            knowledgeContext.prompt(), List.of()));
}
context = Stream.concat(systemMessages.stream(), context.stream()).toList();
```

无 Agent 且无知识库时，`systemMessages` 为空，保持现有上下文。知识库系统消息仍使用 `KnowledgeRetrievalService` 已生成的防提示注入文本。

- [ ] **Step 6: 补充聊天编排测试。**

在 `ChatStreamOrchestratorTest` 验证：

```java
@Test
void putsAgentPromptBeforeKnowledgeContextAndHistory() {
    ChatGenerationCommand command = commandWithSystemPrompt("agent instructions");
    when(messageService.context(anyLong(), anyLong(), isNull()))
            .thenReturn(List.of(new ChatMessagePair(ChatMessageRole.USER, "question", List.of())));
    when(knowledgeRetrievalService.retrieve(anyLong(), anyList(), anyString()))
            .thenReturn(new KnowledgeContext(List.of(source(1L)), "knowledge context"));

    // mock generation, gateway and finalizer so the stream can be consumed

    orchestrator.stream(command, chatModel(2L)).collectList().block();

    ArgumentCaptor<List<ChatMessagePair>> captor = ArgumentCaptor.forClass(List.class);
    verify(aiChatGateway).stream(eq(command), any(), captor.capture());
    assertThat(captor.getValue()).extracting(ChatMessagePair::role)
            .containsExactly(ChatMessageRole.SYSTEM, ChatMessageRole.SYSTEM,
                    ChatMessageRole.USER);
    assertThat(captor.getValue().get(0).content()).isEqualTo("agent instructions");
    assertThat(captor.getValue().get(1).content()).isEqualTo("knowledge context");
}
```

同时验证无 Agent 的 `systemPrompt == null` 时现有消息顺序不变。

- [ ] **Step 7: 运行聊天定向测试。**

Run: `mvn -pl ai-platform-chat -am -Dtest=ChatServiceImplTest,ChatStreamOrchestratorTest test`

Expected: Agent 配置覆盖测试、提示词顺序测试和无 Agent 回归测试全部通过。

---

## Task 7: 文档、全量验证和安全检查

**Files:**
- Modify: `docs/backend-development-guide.md`
- Modify: `docs/frontend-api-integration.md`

- [ ] **Step 1: 更新后端开发文档。**

在后端模块说明中增加 Agent 内容：

- `ai-platform-agent` 的职责和依赖方向；
- `agent_definition`、`agent_knowledge_base`、`agent_tool` 的归属和逻辑删除规则；
- 创建/更新时校验和运行时二次校验；
- 工具仍由管理员维护，用户只绑定可用工具；
- 工具调用继续需要 `tool:execute`。

- [ ] **Step 2: 更新前端接口文档。**

增加以下接口和请求示例：

```text
GET    /api/agents
GET    /api/agents/{id}
POST   /api/agents
PUT    /api/agents/{id}
DELETE /api/agents/{id}
PATCH  /api/agents/{id}/status
```

聊天请求增加：

```json
{
  "content": "帮我查询订单",
  "agentId": 100
}
```

明确说明：Agent 只能由所属用户使用；带 Agent 时模型、知识库和工具由 Agent 配置决定；无 Agent 的请求保持原有行为。

- [ ] **Step 3: 运行 Agent、Chat 和相关工具测试。**

Run:

```bash
mvn -pl ai-platform-agent -am test
mvn -pl ai-platform-chat -am test
mvn -pl ai-platform-tool -am test
```

Expected: 三个模块及其依赖测试全部通过，工具模块既有工具发现、权限和执行行为不变。

- [ ] **Step 4: 运行全量测试和编译。**

Run:

```bash
mvn test
mvn -DskipTests compile
```

Expected: 全部 Maven 模块测试和编译成功。

- [ ] **Step 5: 检查依赖方向和差异。**

Run:

```bash
mvn -q -pl ai-platform-ai dependency:tree -Dincludes=com.example.aichat:ai-platform-agent
mvn -q -pl ai-platform-agent dependency:tree -Dincludes=com.example.aichat:ai-platform-chat
 git diff --check
 git status --short
```

Expected：

- `ai-platform-ai` 不依赖 Agent；
- `ai-platform-agent` 不依赖 Chat；
- 不出现 Agent -> Chat 的反向依赖；
- 工作区只包含本次 Agent 相关修改和原有 `.DS_Store`；
- 不对 `.DS_Store` 执行 `git add`。

- [ ] **Step 6: 做安全回归检查。**

确认以下内容：

- Agent 管理查询始终带 `userId`；
- 客户端不能传入 owner ID；
- Agent 绑定工具只保存 code；
- 工具执行仍进入 `ToolExecutionService`、权限检查和审计；
- 知识库内容仍被标记为不可信上下文；
- Agent system prompt 不会被用户消息或知识库内容替换；
- 无 Agent 请求没有改变模型、知识库、工具和 SSE 行为；
- 测试输出、日志和响应没有凭据、完整工具参数或完整工具响应。

---

## Self-review checklist

- **Spec coverage:** Task 1 covers the new module, Maven dependencies, MySQL schema, and migration. Tasks 2–5 cover the three entities, error codes, user-scoped CRUD, validation, runtime revalidation, and controller. Task 6 covers `agentId`, fixed model/resources, system prompt ordering, regenerate, and legacy chat behavior. Task 7 covers both API documents, module/full tests, dependency direction, and security checks.
- **Placeholder scan:** The plan contains no `TODO`, `TBD`, or unspecified implementation choice. Every planned class, method contract, table, error code, test command, and expected result is named.
- **Type consistency:** `AgentUpsertRequest`, `AgentVO`, `AgentRuntimeConfig`, `AgentService`, and `AgentRuntimeService` use the same `modelId`, `knowledgeBaseIds`, and `toolCodes` types. `ChatRequestDTO.agentId` and `ChatRegenerateDTO.agentId` both resolve to the same `AgentRuntimeService.resolve(Long, Long)` contract. `ChatGenerationCommand.systemPrompt` is consumed only by `ChatStreamOrchestrator`.
- **Compatibility:** Existing Chat DTO constructors and ChatGenerationCommand constructors are explicitly preserved. Requests without `agentId` retain the existing model, knowledge-base, tool, persistence, and SSE paths.
- **Security:** No Agent API accepts an owner ID, and no new path bypasses the existing tool permission or execution service.
