# Admin 模块管理员功能开发文档

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 功能 | 管理首页概览、用户数量统计、会话及消息统计、Token 消耗统计、管理员操作日志 |
| 所属模块 | `ai-platform-admin` |
| 依赖模块 | `ai-platform-system`、`ai-platform-chat`、`ai-platform-ai`、`ai-platform-common` |
| 技术栈 | Spring Boot 3.5、Java 17、MyBatis-Plus、MySQL、Flyway、Sa-Token |
| 访问前缀 | `/api/admin` |
| 权限模型 | 复用 `sys_permission`、`sys_role`、`sys_user_role`、Sa-Token |
| 时间口径 | 所有查询使用半开区间 `[from, to)`，按服务端时区处理 |

本文档是后端开发设计和联调依据。当前仓库没有独立前端模块，因此同时给出管理页面需要的接口和展示字段。

## 2. 当前代码现状与改造结论

当前项目已经具备以下基础能力：

- `ai-platform-admin` 已有 AI Provider、AI Model 管理 Controller。
- `sys_user` 保存用户注册时间、状态和最后登录信息。
- `chat_session`、`chat_message`、`chat_generation_task` 保存会话、消息和生成任务。
- `ai_call_log` 已保存调用状态、模型、输入/输出/总 Token、调用时间和错误信息。
- `sys_operation_log` 表已在 `V1__system_schema.sql` 创建，但尚未有实体、Mapper、Service、切面和查询接口。
- 权限校验已经通过 `@SaCheckPermission` 实现，权限来源为用户-角色-权限关系。

需要补齐的关键点：

1. `ai-platform-admin/pom.xml` 增加对 `ai-platform-system`、`ai-platform-chat` 的依赖，并显式引入 MyBatis-Plus 和 AOP。
2. 在 admin 模块增加统计聚合 Mapper，使用数据库 `count/sum/group by`，禁止将全量明细加载到 JVM 后再统计。
3. 扩展 `AiUsageQueryService`，提供趋势、按模型、按用户等聚合查询，保留 Token 来源 `EXACT/ESTIMATED/UNKNOWN`。
4. 为 `sys_operation_log` 增加实体、分页查询和统一写入机制。
5. 增加管理权限初始化 SQL，并给系统管理 Controller 和 admin Controller 标记操作日志。

## 3. 功能范围

### 3.1 管理首页概览

支持选择今日、近 7 天、近 30 天和自定义时间范围，展示：

- 用户总数、区间新增用户数、区间活跃用户数。
- 会话总数、区间新增会话数。
- 消息总数、区间用户消息数、区间助手消息数。
- AI 调用次数、成功次数、失败次数、成功率。
- 输入 Token、输出 Token、总 Token。
- 最近一天或最近七天的用户、会话、消息、调用和 Token 趋势。

首页的“总数”是当前累计值；“新增/活跃/调用/Token”等均受时间范围过滤。

### 3.2 用户数量统计

统计维度：

- 用户总数：`sys_user.deleted = 0`。
- 启用用户数：`status = 1`。
- 禁用用户数：`status = 0`。
- 区间新增用户数：`create_time ∈ [from, to)`。
- 区间活跃用户数：在 `chat_message` 中产生过有效 USER 消息的去重用户数。
- 每日新增用户和每日活跃用户趋势。

“活跃用户”默认采用产品行为口径，而不是登录口径。若后续需要登录活跃，可基于 `sys_login_log` 增加 `activeType=LOGIN` 查询，不修改默认口径。

### 3.3 会话及消息统计

- 会话总数和区间新增会话数，过滤 `chat_session.deleted = 0`。
- 消息总数，过滤 `chat_message.deleted = 0`。
- 按 `role` 统计 USER、ASSISTANT 消息数。
- 按 `status` 统计 PENDING、STREAMING、COMPLETED、FAILED、INTERRUPTED。
- 每日会话、消息和生成任务趋势。
- 生成成功率：`SUCCESS / (SUCCESS + FAILED + INTERRUPTED)`；RUNNING 不计入分母。

### 3.4 Token 消耗统计

数据源为 `ai_call_log`，统计：

- 调用次数、成功调用、失败调用、取消调用。
- 输入 Token、输出 Token、总 Token。
- 按天、Provider、Model、用户分组。
- Token 来源：精确值、估算值、未知值。
- 平均单次调用 Token、平均响应耗时、失败率。

当前 `TokenUsageCalculator` 使用字符长度估算 Token，并将 `usage_source` 写为 `ESTIMATED`。因此页面必须显示“估算”标记，不能将估算值描述为计费精确值。

### 3.5 管理员操作日志

记录管理员对系统和 admin API 的操作：

- 操作人 ID、用户名。
- 模块、操作名称。
- HTTP 方法、请求 URI、客户端 IP。
- 操作成功/失败。
- 失败消息和发生时间。

默认不保存请求体、响应体、密码、API Key、Token 和聊天内容，避免敏感数据进入日志。需要审计业务参数时，只保存脱敏后的 ID、状态或数量。

## 4. 模块结构设计

建议目录如下：

```text
ai-platform-admin/
└── src/main/java/com/example/aichat/admin/
    ├── controller/
    │   ├── AdminDashboardController.java
    │   ├── AdminStatisticsController.java
    │   └── OperationLogController.java
    ├── service/
    │   ├── AdminDashboardService.java
    │   ├── AdminStatisticsService.java
    │   └── OperationLogService.java
    ├── service/impl/
    ├── mapper/
    │   └── AdminStatisticsMapper.java
    ├── entity/
    │   └── SysOperationLog.java
    ├── dto/request/
    │   ├── StatisticsQuery.java
    │   └── OperationLogQuery.java
    └── dto/response/
        ├── DashboardOverviewDTO.java
        ├── DashboardTrendDTO.java
        ├── UserStatisticsDTO.java
        ├── ChatStatisticsDTO.java
        ├── TokenStatisticsDTO.java
        └── OperationLogDTO.java
└── src/main/resources/
    ├── db/migration/V5__admin_statistics_permission.sql
    └── mapper/AdminStatisticsMapper.xml
```

建议依赖调整：

```xml
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-system</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>com.example.aichat</groupId>
    <artifactId>ai-platform-chat</artifactId>
    <version>${project.version}</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

如果 admin 模块直接声明自定义 MyBatis Mapper，建议同时显式声明 `mybatis-plus-spring-boot3-starter`，不要依赖其他模块的传递依赖。

## 5. API 设计

所有接口返回统一的 `ApiResponse<T>`。时间参数使用 ISO-8601 的本地日期时间，例如 `2026-10-01T00:00:00`。分页参数默认 `page=1,size=20`，`size` 最大 100。

### 5.1 首页概览

#### `GET /api/admin/dashboard/overview`

权限：`admin:dashboard:read`

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `from` | `LocalDateTime` | 是 | 起始时间，包含 |
| `to` | `LocalDateTime` | 是 | 结束时间，不包含 |

响应示例：

```json
{
  "totalUsers": 1200,
  "enabledUsers": 1168,
  "disabledUsers": 32,
  "newUsers": 86,
  "activeUsers": 430,
  "totalSessions": 3580,
  "newSessions": 520,
  "totalMessages": 24800,
  "userMessages": 12400,
  "assistantMessages": 12400,
  "aiCalls": 12400,
  "successfulAiCalls": 12120,
  "failedAiCalls": 280,
  "aiSuccessRate": 0.9774,
  "inputTokens": 1850000,
  "outputTokens": 920000,
  "totalTokens": 2770000,
  "estimatedTokenCalls": 12120
}
```

#### `GET /api/admin/dashboard/trend`

权限：`admin:dashboard:read`

参数：`from`、`to`、`granularity=DAY`。第一期只实现 DAY，后续可扩展 HOUR、WEEK。

响应记录：

```json
{
  "date": "2026-10-15",
  "newUsers": 12,
  "activeUsers": 84,
  "newSessions": 43,
  "messages": 312,
  "aiCalls": 156,
  "successfulAiCalls": 150,
  "totalTokens": 42000
}
```

### 5.2 用户统计

#### `GET /api/admin/statistics/users`

权限：`admin:statistics:user:read`

返回用户总数、启用/禁用数、新增数、活跃数及按天趋势。响应可复用 `DashboardOverviewDTO` 的用户字段，并增加：

```json
{
  "newUsers": 86,
  "activeUsers": 430,
  "trend": [
    { "date": "2026-10-15", "newUsers": 12, "activeUsers": 84 }
  ]
}
```

### 5.3 会话和消息统计

#### `GET /api/admin/statistics/chat`

权限：`admin:statistics:chat:read`

响应字段：

```json
{
  "totalSessions": 3580,
  "newSessions": 520,
  "totalMessages": 24800,
  "userMessages": 12400,
  "assistantMessages": 12400,
  "completedMessages": 12120,
  "failedMessages": 280,
  "generationSuccessRate": 0.9774,
  "trend": []
}
```

### 5.4 Token 统计

#### `GET /api/admin/statistics/tokens`

权限：`admin:statistics:token:read`

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `from` | `LocalDateTime` | 是 | 起始时间 |
| `to` | `LocalDateTime` | 是 | 结束时间 |
| `groupBy` | `DAY/MODEL/PROVIDER/USER` | 否 | 默认 `DAY` |
| `page` | int | 否 | `USER`、`MODEL` 等分组时使用 |
| `size` | int | 否 | 最大 100 |

响应字段：

```json
{
  "calls": 12400,
  "successfulCalls": 12120,
  "failedCalls": 280,
  "inputTokens": 1850000,
  "outputTokens": 920000,
  "totalTokens": 2770000,
  "estimatedTokens": 2770000,
  "averageDurationMs": 1430,
  "groups": [
    {
      "key": "deepseek-chat",
      "name": "DeepSeek Chat",
      "calls": 8200,
      "inputTokens": 1200000,
      "outputTokens": 600000,
      "totalTokens": 1800000,
      "estimated": true
    }
  ]
}
```

### 5.5 操作日志

#### `GET /api/admin/operation-logs`

权限：`admin:operation-log:read`

请求参数：

| 参数 | 类型 | 说明 |
|---|---|---|
| `page,size` | int | 分页，默认 1/20 |
| `operatorId` | long | 操作人 ID |
| `module` | string | 模块，例如 `USER`、`AI_MODEL` |
| `operation` | string | 操作名称 |
| `status` | int | 1 成功，0 失败 |
| `keyword` | string | 用户名、URI 模糊搜索 |
| `from,to` | datetime | 创建时间范围 |

响应使用 `PageResult<OperationLogDTO>`。日志详情字段不返回任何原始密码、API Key、Token 或聊天内容。

第一期不提供删除日志接口。日志属于审计数据，只允许查询；如需归档或清理，应通过受控的数据保留任务完成。

## 6. 统计口径与 SQL 设计

### 6.1 通用约束

所有 SQL 必须满足：

- 用户表过滤 `deleted = 0`。
- 会话表过滤 `deleted = 0`。
- 消息表过滤 `deleted = 0`。
- `ai_call_log` 使用 `start_time >= #{from} and start_time < #{to}`。
- 统计类查询优先使用聚合 SQL，不能调用 `selectList` 后在 Java Stream 中全量计算。
- 空结果返回 0，不返回 null。

### 6.2 核心统计 SQL 示例

用户总数和状态分布：

```sql
select
    count(*) as total_users,
    sum(case when status = 1 then 1 else 0 end) as enabled_users,
    sum(case when status = 0 then 1 else 0 end) as disabled_users
from sys_user
where deleted = 0;
```

新增用户：

```sql
select count(*)
from sys_user
where deleted = 0
  and create_time >= #{from}
  and create_time < #{to};
```

活跃用户：

```sql
select count(distinct user_id)
from chat_message
where deleted = 0
  and role = 'USER'
  and create_time >= #{from}
  and create_time < #{to};
```

会话和消息：

```sql
select count(*)
from chat_session
where deleted = 0;

select
    count(*) as total_messages,
    sum(case when role = 'USER' then 1 else 0 end) as user_messages,
    sum(case when role = 'ASSISTANT' then 1 else 0 end) as assistant_messages
from chat_message
where deleted = 0
  and create_time >= #{from}
  and create_time < #{to};
```

Token 汇总：

```sql
select
    count(*) as calls,
    sum(case when status = 'SUCCESS' then 1 else 0 end) as successful_calls,
    sum(case when status = 'FAILED' then 1 else 0 end) as failed_calls,
    coalesce(sum(input_tokens), 0) as input_tokens,
    coalesce(sum(output_tokens), 0) as output_tokens,
    coalesce(sum(total_tokens), 0) as total_tokens,
    coalesce(sum(case when usage_source = 'ESTIMATED' then total_tokens else 0 end), 0) as estimated_tokens,
    coalesce(avg(duration_ms), 0) as average_duration_ms
from ai_call_log
where start_time >= #{from}
  and start_time < #{to};
```

按模型统计：

```sql
select model_id, model_code, provider_name,
       count(*) as calls,
       coalesce(sum(input_tokens), 0) as input_tokens,
       coalesce(sum(output_tokens), 0) as output_tokens,
       coalesce(sum(total_tokens), 0) as total_tokens
from ai_call_log
where start_time >= #{from}
  and start_time < #{to}
group by model_id, model_code, provider_name
order by total_tokens desc;
```

### 6.3 趋势查询实现

第一期建议采用 MySQL `date_format` 按日聚合：

```sql
select date_format(create_time, '%Y-%m-%d') as stat_date,
       count(*) as messages
from chat_message
where deleted = 0
  and create_time >= #{from}
  and create_time < #{to}
group by date_format(create_time, '%Y-%m-%d')
order by stat_date;
```

用户、会话、消息、AI 调用和 Token 分别查询后，在 Service 层按日期合并，补齐没有数据的日期为 0。不要使用多表直接连接后同时计数，否则会产生笛卡尔放大。

当 `ai_call_log` 超过约 100 万行后，应增加按月汇总表，例如 `admin_daily_statistics`，由定时任务增量汇总；在此之前通过索引和聚合 SQL 即可满足第一期需求。

## 7. 数据库与索引改造

新增 `V5__admin_statistics_permission.sql`，内容包括权限初始化和必要索引：

```sql
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Admin dashboard read', 'admin:dashboard:read', 3, 100, 1),
    (0, 'Admin user statistics read', 'admin:statistics:user:read', 3, 101, 1),
    (0, 'Admin chat statistics read', 'admin:statistics:chat:read', 3, 102, 1),
    (0, 'Admin token statistics read', 'admin:statistics:token:read', 3, 103, 1),
    (0, 'Admin operation log read', 'admin:operation-log:read', 3, 104, 1);

create index idx_sys_user_deleted_create_time
    on sys_user (deleted, create_time);

create index idx_chat_session_deleted_create_time
    on chat_session (deleted, create_time);

create index idx_chat_message_deleted_role_time_user
    on chat_message (deleted, role, create_time, user_id);

create index idx_ai_call_log_start_status_usage
    on ai_call_log (start_time, status, usage_source);

create index idx_sys_operation_log_time_status
    on sys_operation_log (create_time, status);
```

如果 `sys_permission` 当前没有“管理员”角色初始化数据，应在同一迁移或独立迁移中为现有管理员角色绑定上述权限；不能默认给所有用户授权。

现有 `sys_operation_log` 表第一期可以直接复用。建议后续按需增加：

```sql
alter table sys_operation_log add column duration_ms bigint null after status;
alter table sys_operation_log add key idx_operation_module_time (module, create_time);
```

不建议第一期增加原始请求参数列；若审计确实需要，必须使用 JSON 脱敏后再保存。

## 8. 操作日志实现方案

### 8.1 注解

在 `ai-platform-common` 新增 `@AdminOperation`：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOperation {
    String module();
    String operation();
}
```

在需要审计的接口上使用，例如：

```java
@AdminOperation(module = "AI_MODEL", operation = "CREATE")
@PostMapping
@SaCheckPermission("ai:model:create")
public ApiResponse<AiModelManagementDTO> create(...) { ... }
```

建议覆盖：

- `ai-platform-admin` 下 Provider、Model 的新增、修改、删除。
- `ai-platform-system` 下用户状态修改、角色分配、角色新增/修改/删除、权限新增/修改/删除。
- 统计查询和日志查询可不记录，避免查询日志自身造成噪声。

### 8.2 切面行为

`OperationLogAspect` 放在 admin 模块，切点为 `@annotation(AdminOperation)`：

1. 进入方法时获取当前登录用户、URI、HTTP 方法和 IP。
2. 执行业务方法。
3. 成功时写入 `status=1`。
4. 抛出异常时写入 `status=0`、错误码和脱敏后的错误信息，然后继续抛出原异常。
5. 写日志使用独立事务 `REQUIRES_NEW`，业务事务回滚时操作日志仍然保留。
6. 日志写入失败不能覆盖原业务异常，必要时降级为应用日志告警。

IP 获取需要处理反向代理场景，按可信代理配置读取 `X-Forwarded-For`，不能无条件信任任意客户端传入的 Header。

## 9. 权限与安全要求

- 所有 `/api/admin/**` 接口必须同时具备登录校验和 `@SaCheckPermission`。
- 统计接口只返回聚合结果，不返回聊天内容、邮箱全量、手机号全量或 API Key。
- Token 统计按用户分组时，普通管理员是否可以查看用户维度需要单独授权；建议将 `USER` 分组权限拆为 `admin:statistics:token:user:read`。
- 操作日志只允许有审计权限的管理员查询。
- 时间范围限制最大 366 天，避免单次查询拖垮数据库。
- `page`、`size`、`groupBy`、排序字段使用白名单校验，禁止将前端参数直接拼接到 SQL。
- 统计接口不允许通过用户输入拼接表名或字段名。
- 生产环境对 `sys_operation_log` 设置数据保留周期，例如 180 或 365 天，并采用归档后清理策略。

## 10. 测试方案

### 10.1 单元测试

- `StatisticsService`：空数据、单日数据、跨日数据、边界时间 `[from,to)`。
- Token 汇总：null Token、ESTIMATED、EXACT、失败调用、取消调用。
- 百分比计算：分母为 0 时返回 0，不产生 NaN 或异常。
- 趋势合并：不同来源日期不一致时能补 0 并正确合并。
- `OperationLogAspect`：成功、业务异常、权限异常、日志写入失败不影响原异常。
- 查询参数校验：最大时间跨度、非法 `groupBy`、超大分页参数。

### 10.2 Mapper/集成测试

- Flyway 从 V1 到 V5 顺序执行成功。
- 逻辑删除数据不进入任何统计。
- 多用户、多会话、多消息、多 AI 调用组合下，统计结果无重复计数。
- 同一时间边界的数据只属于一个统计区间。
- 操作日志按操作人、模块、状态、时间分页查询正确。

### 10.3 API 验收标准

1. 未登录访问 admin 接口返回未认证错误。
2. 已登录但无对应权限返回无权限错误。
3. 管理首页在无业务数据时所有数字返回 0，趋势返回完整日期序列。
4. 用户、会话、消息和 Token 汇总值与直接 SQL 核对一致。
5. 修改用户状态、分配角色、修改 AI Model 后产生一条成功操作日志。
6. 业务操作失败时产生一条失败操作日志，日志包含错误状态但不包含密码、Key、Token 或聊天内容。
7. Token 统计明确区分估算值和精确值。
8. 366 天范围内常用统计接口在测试数据规模下满足接口 SLA；建议第一期目标为 P95 < 1 秒，复杂分组查询 P95 < 2 秒。

## 11. 开发顺序

### 阶段一：基础结构

1. 调整 admin 模块 Maven 依赖。
2. 新增 V5 权限和索引迁移。
3. 新增请求参数、响应 DTO 和统一时间范围校验器。

### 阶段二：统计查询

1. 新增 `AdminStatisticsMapper` 和 XML 聚合 SQL。
2. 实现用户、会话、消息、Token 统计 Service。
3. 实现首页 overview 和 trend 接口。
4. 增加分页分组查询和缓存评估。

### 阶段三：操作日志

1. 在 common 增加 `@AdminOperation`。
2. 在 admin 模块实现实体、Mapper、Service、Aspect。
3. 标注 admin/system 管理写接口。
4. 实现日志分页查询接口。

### 阶段四：联调与性能

1. 使用固定测试数据校对所有统计口径。
2. 验证权限、脱敏和异常日志。
3. 执行 Explain，确认统计 SQL 使用索引。
4. 完成 Maven 全量测试和启动验证。

## 12. 交付物清单

- `ai-platform-admin` 新增 Controller、Service、Mapper、DTO、Aspect 和测试。
- `ai-platform-common` 新增 `@AdminOperation` 注解。
- `V5__admin_statistics_permission.sql` 数据库迁移。
- `AiUsageQueryService` 的聚合查询扩展及其测试。
- API 文档或 OpenAPI 导出文件。
- 统计口径说明和管理员权限配置说明。
- 测试报告：权限、边界时间、逻辑删除、Token 来源、操作日志脱敏。

