# Admin Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (native execution is selected for this task). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the admin dashboard, user, chat, and Token statistics APIs described in the admin module specification.

**Architecture:** Add a focused read-only `AdminStatisticsMapper` in `ai-platform-admin` with SQL aggregation over existing system, chat, and AI tables. An `AdminStatisticsService` validates date ranges, combines daily aggregates, and maps results into API DTOs; controllers expose permission-protected endpoints.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus/MyBatis XML, MySQL, Flyway, Sa-Token, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-16-admin-module-management-design.md`

## Global Constraints

- Do not create or restore `sys_login_log` or `sys_operation_log`.
- Do not add admin statistics detail tables in this phase.
- Use time range `[from, to)` and reject ranges longer than 366 days.
- Filter logical-deleted users, sessions, and messages with `deleted = 0`.
- Use aggregate SQL; do not load full detail tables into JVM for statistics.
- Protect all admin endpoints with `@SaCheckPermission`.

## Review Focus

- Empty datasets must return numeric zeroes and complete zero-filled trend dates; test in service.
- End timestamps are exclusive and adjacent ranges must not double-count; test query/service date handling.
- Invalid, reversed, or over-366-day ranges must fail with `VALIDATION_ERROR`; test query validation.
- Token nulls and estimated usage must aggregate safely and preserve estimated totals; test token mapping.
- Dynamic `groupBy` must be whitelist-controlled and never become raw user SQL; test all supported values and rejection.

### Task 1: Module wiring and migration verification

**Files:**
- Modify: `ai-platform-admin/pom.xml`
- Modify: `ai-platform-boot/src/main/java/com/example/aichat/boot/AiPlatformApplication.java`
- Verify: `ai-platform-admin/src/main/resources/db/migration/V5__admin_statistics_permission.sql`

**Interfaces:**
- Produces admin module access to system/chat entities and scanning for `com.example.aichat.admin.mapper`.

- [x] Add direct `ai-platform-system`, `ai-platform-chat`, and MyBatis-Plus dependencies.
- [x] Add the admin mapper package to `@MapperScan`.
- [x] Confirm V5 contains only admin statistic permissions and statistic indexes.
- [x] Run the focused Maven compile after the first implementation slice.

### Task 2: Request, projection, and response DTOs

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/request/StatisticsQuery.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/DashboardOverviewDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/DashboardTrendDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/UserStatisticsDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/UserTrendDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/ChatStatisticsDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/ChatTrendDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/TokenStatisticsDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/TokenGroupDTO.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/dto/response/DailyStatisticsRow.java`

**Interfaces:**
- `StatisticsQuery(LocalDateTime from, LocalDateTime to)` as a Spring `@ModelAttribute` with `validate()`.
- Response records expose the documented JSON field names and numeric zero defaults.

- [x] Write tests for valid, reversed, null, and over-366-day ranges.
- [x] Implement immutable response records and a mutable MyBatis daily projection row.
- [x] Run DTO/service validation tests.

### Task 3: Aggregate Mapper and XML

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/mapper/AdminStatisticsMapper.java`
- Create: `ai-platform-admin/src/main/resources/mapper/AdminStatisticsMapper.xml`

**Interfaces:**
- `Map<String,Object> selectUserOverview()`
- `long countNewUsers(LocalDateTime from, LocalDateTime to)`
- `long countActiveUsers(LocalDateTime from, LocalDateTime to)`
- `Map<String,Object> selectSessionOverview()`
- `Map<String,Object> selectMessageOverview(LocalDateTime from, LocalDateTime to)`
- `Map<String,Object> selectGenerationOverview(LocalDateTime from, LocalDateTime to)`
- `Map<String,Object> selectAiOverview(LocalDateTime from, LocalDateTime to)`
- Daily trend methods returning `List<DailyStatisticsRow>` for user, active user, session, message, generation, and AI metrics.
- Token summary and group methods returning aggregate `Map<String,Object>` rows.

- [x] Add SQL using exclusive `to`, logical-delete filters, and `coalesce` for null sums.
- [x] Add separate daily queries and merge in service to avoid multi-table count multiplication.
- [x] Add token group queries for `DAY`, `MODEL`, `PROVIDER`, and `USER`; do not interpolate unchecked groupBy input.
- [x] Verify mapper XML namespace and result aliases match Java fields.

### Task 4: Statistics service

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/service/AdminStatisticsService.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/service/impl/AdminStatisticsServiceImpl.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/enums/TokenGroupBy.java`

**Interfaces:**
- `DashboardOverviewDTO overview(StatisticsQuery query)`
- `List<DashboardTrendDTO> dashboardTrend(StatisticsQuery query)`
- `UserStatisticsDTO userStatistics(StatisticsQuery query)`
- `ChatStatisticsDTO chatStatistics(StatisticsQuery query)`
- `TokenStatisticsDTO tokenStatistics(StatisticsQuery query, String groupBy, int page, int size)`

- [x] Validate date range and pagination bounds before mapper calls.
- [x] Merge daily rows by `LocalDate` and fill every date in the requested range with zeroes.
- [x] Calculate rates as zero when denominator is zero.
- [x] Map MyBatis numeric values defensively, including null Token columns.
- [x] Whitelist groupBy and apply pagination to grouped token rows.
- [x] Add service unit tests for all review-focus cases.

### Task 5: Admin controllers

**Files:**
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminDashboardController.java`
- Create: `ai-platform-admin/src/main/java/com/example/aichat/admin/controller/AdminStatisticsController.java`

**Interfaces:**
- `GET /api/admin/dashboard/overview`
- `GET /api/admin/dashboard/trend`
- `GET /api/admin/statistics/users`
- `GET /api/admin/statistics/chat`
- `GET /api/admin/statistics/tokens`

- [x] Bind `StatisticsQuery` from query parameters.
- [x] Add exact `@SaCheckPermission` values from V5.
- [x] Return `ApiResponse<T>` consistently.
- [x] Add controller tests for permission annotations and response delegation where practical.

### Task 6: Verification and cleanup

**Files:**
- Modify only files required by compiler/test feedback.

- [x] Run admin module tests.
- [x] Run `mvn test` for the full reactor.
- [x] Run `git diff --check`.
- [x] Re-scan the admin implementation to ensure no login/operation log table, endpoint, or permission was added.
- [x] Report any environment-dependent Flyway/database verification separately from Java test results.
