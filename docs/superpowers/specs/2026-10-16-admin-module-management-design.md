# Admin 模块统计功能开发文档

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 功能范围 | 管理首页概览、用户数量统计、会话及消息统计、Token 消耗统计 |
| 所属模块 | `ai-platform-admin` |
| 依赖模块 | `ai-platform-system`、`ai-platform-chat`、`ai-platform-ai`、`ai-platform-common` |
| 技术栈 | Spring Boot 3.5、Java 17、MyBatis-Plus、MySQL、Flyway、Sa-Token |
| API 前缀 | `/api/admin` |
| 权限模型 | 复用系统 RBAC 和 Sa-Token |
| 时间口径 | 所有时间查询使用半开区间 `[from, to)` |

本文档只覆盖 admin 统计功能。`sys_login_log`、`sys_operation_log` 不属于本期需求，不新增、不恢复、不提供相关接口。

## 2. 目标与非目标

### 2.1 本期目标

通过现有业务明细表完成以下能力：

- 管理首页展示系统运行概况。
- 统计用户总数、新增用户数和活跃用户数。
- 统计会话、消息和生成任务数量及趋势。
- 统计 AI 调用次数、成功率、Token 消耗和模型维度数据。
- 使用现有系统权限表控制 admin 接口访问。

### 2.2 本期不做

- 不建设登录日志功能。
- 不建设管理员操作审计日志功能。
- 不新建 `admin_user_statistics`、`admin_chat_statistics`、`admin_token_usage` 等统计明细表。
- 不新建按日汇总表；数据量增大后再评估 `admin_daily_statistics`。
- 不在统计接口返回聊天正文、用户密码、API Key 或其他敏感信息。

## 3. 当前表结构与数据来源

第一期 admin 直接查询下列表，不新增 admin 业务表。

| 表 | 所属领域 | 用途 |
|---|---|---|
| `sys_user` | system | 用户总数、状态、新增用户、活跃用户 |
| `sys_role` | system | 管理员角色校验 |
| `sys_permission` | system | admin 权限定义 |
| `sys_user_role` | system | 用户与角色关系 |
| `sys_role_permission` | system | 角色与权限关系 |
| `chat_session` | chat | 会话总量、新增会话、会话趋势 |
| `chat_message` | chat | 用户消息、助手消息、消息状态、活跃用户 |
| `chat_generation_task` | chat | 生成任务成功、失败、中断和运行中数量 |
| `ai_provider` | ai | Provider 维度展示 |
| `ai_model` | ai | Model 维度展示 |
| `ai_call_log` | ai | AI 调用、Token、耗时和失败统计 |

以下表不纳入本期 admin 功能：

```text
sys_login_log
sys_operation_log
```

注意：历史 `V1__system_schema.sql` 中仍可能存在 `sys_operation_log` 的旧建表语句，本期不修改历史迁移文件，也不在 admin 的 V5 迁移中重新创建该表。

## 4. 统计口径

### 4.1 用户统计

- 用户总数：`sys_user.deleted = 0`。
- 启用用户数：`deleted = 0 and status = 1`。
- 禁用用户数：`deleted = 0 and status = 0`。
- 新增用户数：`create_time ∈ [from, to)`。
- 活跃用户数：在时间范围内产生过 USER 消息的去重用户数。

本期活跃用户采用产品行为口径，不使用登录日志。用户登录但没有产生聊天行为时，不计入聊天活跃用户。

### 4.2 会话统计

- 会话总数：`chat_session.deleted = 0`。
- 新增会话：`create_time ∈ [from, to)`。
- 归档或逻辑删除会话不计入统计。

### 4.3 消息与生成任务统计

- 消息总数：`chat_message.deleted = 0`。
- 用户消息：`role = USER`。
- 助手消息：`role = ASSISTANT`。
- 消息状态：`PENDING`、`STREAMING`、`COMPLETED`、`FAILED`、`INTERRUPTED`。
- 生成任务状态：`RUNNING`、`SUCCESS`、`FAILED`、`INTERRUPTED`。
- 生成成功率：`SUCCESS / (SUCCESS + FAILED + INTERRUPTED)`。
- `RUNNING` 不计入成功率分母。

### 4.4 Token 统计

数据源为 `ai_call_log`：

- 调用次数：全部调用记录数。
- 成功调用：`status = SUCCESS`。
- 失败调用：`status = FAILED`。
- 取消调用：`status = CANCELLED`。
- 输入 Token：`sum(input_tokens)`。
- 输出 Token：`sum(output_tokens)`。
- 总 Token：`sum(total_tokens)`。
- 平均耗时：`avg(duration_ms)`。
- Token 来源：使用 `usage_source` 区分精确值和估算值。

当前 `TokenUsageCalculator` 使用字符长度估算 Token，并写入 `ESTIMATED`，前端需要明确展示“估算值”，不能当作计费精确值。

## 5. 模块结构调整

### 5.1 Maven 依赖

`ai-platform-admin/pom.xml` 需要补充对 system 和 chat 的依赖：

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
```

如果 admin 直接声明自定义 MyBatis Mapper，建议显式声明 `mybatis-plus-spring-boot3-starter`，不要依赖其他模块的传递依赖。

### 5.2 推荐目录

```text
ai-platform-admin/
├── src/main/java/com/example/aichat/admin/
│   ├── controller/
│   │   ├── AdminDashboardController.java
│   │   └── AdminStatisticsController.java
│   ├── service/
│   │   ├── AdminDashboardService.java
│   │   └── AdminStatisticsService.java
│   ├── service/impl/
│   ├── mapper/
│   │   └── AdminStatisticsMapper.java
│   └── dto/
│       ├── request/StatisticsQuery.java
│       └── response/
│           ├── DashboardOverviewDTO.java
│           ├── DashboardTrendDTO.java
│           ├── UserStatisticsDTO.java
│           ├── ChatStatisticsDTO.java
│           └── TokenStatisticsDTO.java
└── src/main/resources/
    ├── db/migration/V5__admin_statistics_permission.sql
    └── mapper/AdminStatisticsMapper.xml
```

统计查询使用独立的 `AdminStatisticsMapper`，不要在 Controller 中直接拼装 SQL，也不要将全量明细加载到 JVM 后再统计。

## 6. API 设计

所有接口返回统一的 `ApiResponse<T>`。时间参数使用 `LocalDateTime`，例如 `2026-10-01T00:00:00`。时间范围最大 366 天，分页 `size` 最大 100。

### 6.1 首页概览

#### `GET /api/admin/dashboard/overview`

权限：`admin:dashboard:read`

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `from` | `LocalDateTime` | 是 | 起始时间，包含 |
| `to` | `LocalDateTime` | 是 | 结束时间，不包含 |

响应字段：

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
  "totalTokens": 2770000
}
```

### 6.2 首页趋势

#### `GET /api/admin/dashboard/trend`

权限：`admin:dashboard:read`

请求参数：`from`、`to`、`granularity=DAY`。第一期只实现按天统计。

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

### 6.3 用户统计

#### `GET /api/admin/statistics/users`

权限：`admin:statistics:user:read`

返回用户总数、启用/禁用数、新增数、活跃数及按天趋势。

### 6.4 会话及消息统计

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

### 6.5 Token 统计

#### `GET /api/admin/statistics/tokens`

权限：`admin:statistics:token:read`

请求参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `from` | `LocalDateTime` | 是 | 起始时间 |
| `to` | `LocalDateTime` | 是 | 结束时间 |
| `groupBy` | `DAY/MODEL/PROVIDER/USER` | 否 | 默认 `DAY` |
| `page` | int | 否 | 分组查询分页 |
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

## 7. SQL 与索引设计

### 7.1 通用约束

- `sys_user` 过滤 `deleted = 0`。
- `chat_session` 过滤 `deleted = 0`。
- `chat_message` 过滤 `deleted = 0`。
- `ai_call_log` 使用 `start_time >= #{from} and start_time < #{to}`。
- 空结果统一返回 0，不返回 null。
- 不使用多表直接连接同时统计不同明细，否则可能出现计数放大。
- 趋势查询分别聚合，在 Service 层按日期合并并补 0。

### 7.2 用户统计

```sql
select
    count(*) as total_users,
    sum(case when status = 1 then 1 else 0 end) as enabled_users,
    sum(case when status = 0 then 1 else 0 end) as disabled_users
from sys_user
where deleted = 0;
```

```sql
select count(*)
from sys_user
where deleted = 0
  and create_time >= #{from}
  and create_time < #{to};
```

```sql
select count(distinct user_id)
from chat_message
where deleted = 0
  and role = 'USER'
  and create_time >= #{from}
  and create_time < #{to};
```

### 7.3 会话与消息统计

```sql
select count(*)
from chat_session
where deleted = 0;
```

```sql
select
    count(*) as total_messages,
    sum(case when role = 'USER' then 1 else 0 end) as user_messages,
    sum(case when role = 'ASSISTANT' then 1 else 0 end) as assistant_messages
from chat_message
where deleted = 0
  and create_time >= #{from}
  and create_time < #{to};
```

### 7.4 Token 统计

```sql
select
    count(*) as calls,
    sum(case when status = 'SUCCESS' then 1 else 0 end) as successful_calls,
    sum(case when status = 'FAILED' then 1 else 0 end) as failed_calls,
    sum(case when status = 'CANCELLED' then 1 else 0 end) as cancelled_calls,
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

### 7.5 趋势查询

第一期采用 MySQL `date_format` 按日聚合：

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

用户、会话、消息、AI 调用和 Token 分别查询后，在 Service 层按日期合并，并补齐没有数据的日期为 0。

当 `ai_call_log` 超过约 100 万行后，再评估增加 `admin_daily_statistics` 汇总表；第一期不引入汇总任务和额外数据一致性问题。

## 8. Flyway 迁移

当前迁移文件：

```text
ai-platform-admin/src/main/resources/db/migration/V5__admin_statistics_permission.sql
```

该文件只包含 admin 统计权限和统计索引，不创建任何日志表：

```sql
insert ignore into sys_permission
    (parent_id, permission_name, permission_code, permission_type, sort, status)
values
    (0, 'Admin dashboard read', 'admin:dashboard:read', 3, 100, 1),
    (0, 'Admin user statistics read', 'admin:statistics:user:read', 3, 101, 1),
    (0, 'Admin chat statistics read', 'admin:statistics:chat:read', 3, 102, 1),
    (0, 'Admin token statistics read', 'admin:statistics:token:read', 3, 103, 1);

create index idx_sys_user_deleted_create_time
    on sys_user (deleted, create_time);

create index idx_chat_session_deleted_create_time
    on chat_session (deleted, create_time);

create index idx_chat_message_deleted_role_time_user
    on chat_message (deleted, role, create_time, user_id);

create index idx_ai_call_log_start_status_usage
    on ai_call_log (start_time, status, usage_source);
```

如果现有管理员角色没有绑定这些权限，需要单独配置角色权限关系，不能默认授权所有用户。

## 9. 权限与安全要求

- 所有 `/api/admin/**` 接口必须同时具备登录校验和 `@SaCheckPermission`。
- `groupBy` 仅允许 `DAY`、`MODEL`、`PROVIDER`、`USER` 白名单值。
- 时间范围最大 366 天，分页大小最大 100。
- 排序字段只能使用后端白名单，禁止将前端参数直接拼接到 SQL。
- 统计接口只返回聚合字段，不返回聊天正文、密码、手机号全量或 API Key。
- 用户维度 Token 统计可能涉及敏感运营数据，建议后续拆分权限 `admin:statistics:token:user:read`。
- 统计接口失败时返回统一业务错误，不暴露数据库异常和 SQL 内容。

## 10. 测试与验收

### 10.1 单元测试

- 空数据时所有汇总字段为 0。
- 单日、跨日和边界时间 `[from, to)` 计算正确。
- 用户、会话、消息逻辑删除后不进入统计。
- Token 为 null 时按 0 处理。
- `ESTIMATED`、`EXACT`、未知来源分类正确。
- 成功率分母为 0 时返回 0，不产生 NaN。
- 趋势合并时缺失日期补 0。
- 非法 `groupBy`、超大时间范围和超大分页参数被拒绝。

### 10.2 Mapper/集成测试

- Flyway 从 V1 到 V5 执行成功。
- 多用户、多会话、多消息和多 AI 调用组合下不存在重复计数。
- 相邻时间范围不会重复统计边界数据。
- 按天和按模型聚合结果与直接 SQL 结果一致。
- 统计查询使用预期索引，并通过 `EXPLAIN` 检查执行计划。

### 10.3 API 验收标准

1. 未登录访问 admin 接口返回未认证错误。
2. 已登录但无对应权限返回无权限错误。
3. 无业务数据时首页卡片全部返回 0，趋势返回完整日期序列。
4. 用户、会话、消息和 Token 汇总值与数据库直接聚合结果一致。
5. Token 统计明确区分估算值和精确值。
6. 不存在操作日志接口，也不依赖 `sys_login_log` 或 `sys_operation_log`。
7. 常用统计接口目标 P95 小于 1 秒，复杂分组查询目标 P95 小于 2 秒。

## 11. 开发顺序与交付物

### 11.1 开发顺序

1. 调整 `ai-platform-admin/pom.xml`，增加 system、chat 和 MyBatis 相关依赖。
2. 完善 V5 权限和索引迁移。
3. 新增统计请求参数、响应 DTO 和时间范围校验。
4. 新增 `AdminStatisticsMapper` 及 XML 聚合 SQL。
5. 实现用户、会话、消息、生成任务和 Token 统计 Service。
6. 实现首页概览、趋势、用户、聊天、Token 接口。
7. 编写单元测试、Mapper 测试和 API 权限测试。
8. 执行 `EXPLAIN` 和 Maven 全量测试。

### 11.2 交付物

- Admin 统计 Controller、Service、Mapper、DTO 和测试。
- `V5__admin_statistics_permission.sql`。
- `AiUsageQueryService` 的聚合查询扩展。
- API 文档或 OpenAPI 定义。
- 统计口径说明和权限配置说明。
- 测试报告和统计 SQL 执行计划。
