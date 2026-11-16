# 异步 AI 工具用户上下文修复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 HTTP/MCP 工具在 `boundedElastic` 异步线程中执行时不再依赖 Servlet Sa-Token 上下文，同时保留用户权限校验和调用审计。

**Architecture:** AI 工具链路使用已经捕获的显式 `userId` 完成权限判断、工具执行和审计。面向普通同步管理接口的 `execute()` 仍使用当前请求上下文；仅面向 AI 异步调用的 `executeAsUser()` 改为显式用户路径，不传播 Servlet 请求对象或 ThreadLocal 上下文。

**Tech Stack:** Java 17, Spring Boot, Sa-Token 1.46, MyBatis-Plus, JUnit 5, Mockito, Reactor.

---

### Task 1: 为工具预检移除异步 Sa-Token 上下文依赖

**Files:**
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/ai/ToolAiToolProvider.java:57-65`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/ai/ToolAiToolProviderTest.java`

- [ ] **Step 1: Write the failing test**

创建 `ToolAiToolProviderTest`，使用 Mockito 静态 Mock 验证预检只按显式用户 ID 检查权限，不调用 `StpUtil.switchTo`：

```java
@Test
void preparesToolsWithoutServletSaTokenContext() {
    try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
        stp.when(() -> StpUtil.hasPermission(42L, "tool:execute")).thenReturn(true);
        when(toolMapper.selectList(any())).thenReturn(List.of(enabledHttpDefinition()));

        AiToolContext context = provider.prepare(42L, "request-1", List.of("weather"));

        assertThat(context.definitions()).hasSize(1);
        stp.verify(() -> StpUtil.hasPermission(42L, "tool:execute"));
        stp.verifyNoMoreInteractions();
    }
}
```

测试夹具提供 `ToolDefinitionMapper`、`ToolExecutionService`、`McpServerManagementService`、`ToolConfigCodec`、`HttpToolSchemaGenerator` 和 `ObjectMapper` Mock；HTTP 工具定义包含合法的 `httpConfig`，并将 `toolType` 设为 `ToolType.HTTP`、`status` 设为 `1`、`availability` 设为 `ToolAvailability.AVAILABLE`。

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolAiToolProviderTest test
```

Expected: FAIL，因为当前 `prepare()` 仍调用 `StpUtil.switchTo(userId, ...)`，没有调用显式用户权限 API。

- [ ] **Step 3: Write the minimal implementation**

将 `ToolAiToolProvider.prepare()` 中的：

```java
StpUtil.switchTo(userId, () -> StpUtil.checkPermission(EXECUTE_PERMISSION));
```

替换为：

```java
if (!StpUtil.hasPermission(userId, EXECUTE_PERMISSION)) {
    throw new BusinessException(ErrorCode.FORBIDDEN);
}
```

保留 `EXECUTE_PERMISSION` 常量和后续工具定义查询逻辑不变。

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolAiToolProviderTest test
```

Expected: PASS，且测试不需要初始化 Servlet Sa-Token 上下文。

---

### Task 2: 让 `executeAsUser` 在异步线程中使用显式用户 ID

**Files:**
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImpl.java:51-117`
- Test: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

在现有测试中增加显式用户执行测试。静态 Mock 显式权限判断，并验证执行时不访问 `CurrentUserProvider`：

```java
@Test
void executeAsUserUsesExplicitUserIdWithoutCurrentSaTokenContext() {
    ToolDefinition definition = definition();
    when(toolMapper.selectOne(any())).thenReturn(definition);
    when(executor.toolType()).thenReturn(ToolType.HTTP);
    when(executor.execute(any(), any())).thenReturn(new ToolExecutionResult(
            "client-1", "orders", ToolType.HTTP, true, 200, Map.of("ok", true), 12));

    try (MockedStatic<StpUtil> stp = Mockito.mockStatic(StpUtil.class)) {
        stp.when(() -> StpUtil.hasPermission(42L, "tool:execute")).thenReturn(true);

        ToolExecutionResult result = service.executeAsUser(
                42L, new ToolExecuteRequest("orders", Map.of("id", 1), "client-1"));

        assertThat(result.success()).isTrue();
        verify(currentUserProvider, never()).requireCurrentUser();
        ArgumentCaptor<ToolInvocationLog> captor = ArgumentCaptor.forClass(ToolInvocationLog.class);
        verify(logService).record(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        stp.verify(() -> StpUtil.hasPermission(42L, "tool:execute"));
        stp.verifyNoMoreInteractions();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionServiceImplTest test
```

Expected: FAIL，因为当前实现调用 `StpUtil.switchTo()`，并且 `executeInternal()` 通过 `CurrentUserProvider.requireCurrentUser()` 获取用户。

- [ ] **Step 3: Write the minimal implementation**

保留同步接口 `execute()` 的现有行为，但先捕获当前用户并将其 ID 传给内部方法：

```java
@Override
@Transactional
public ToolExecutionResult execute(ToolExecuteRequest request) {
    StpUtil.checkPermission("tool:execute");
    return executeInternal(currentUserProvider.requireCurrentUser().id(), request);
}
```

将异步接口改为显式权限判断和显式用户执行：

```java
@Override
@Transactional
public ToolExecutionResult executeAsUser(Long userId, ToolExecuteRequest request) {
    if (!StpUtil.hasPermission(userId, "tool:execute")) {
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    return executeInternal(userId, request);
}
```

修改内部方法签名：

```java
private ToolExecutionResult executeInternal(Long userId, ToolExecuteRequest request)
```

删除内部的：

```java
CurrentUser user = currentUserProvider.requireCurrentUser();
```

并将成功、失败审计中的 `user.id()` 替换为 `userId`。删除不再使用的 `CurrentUser` import；保留 `CurrentUserProvider` 用于同步 `execute()`。

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl ai-platform-tool -am -Dtest=ToolExecutionServiceImplTest test
```

Expected: PASS，包含原有同步执行、失败审计、禁用工具测试以及新增异步显式用户测试。

---

### Task 3: 执行完整验证

**Files:**
- No source changes expected.

- [ ] **Step 1: Run focused tool tests**

```bash
mvn -pl ai-platform-tool -am -Dtest='ToolAiToolProviderTest,ToolExecutionServiceImplTest,HttpToolExecutorTest,McpToolExecutorTest' test
```

Expected: PASS。

- [ ] **Step 2: Run the full Maven test suite**

```bash
mvn test
```

Expected: PASS，且没有因 `executeInternal` 签名变化导致的编译错误。

- [ ] **Step 3: Review the final diff**

```bash
git diff --check
git diff -- ai-platform-tool/src/main/java/com/example/aichat/tool/ai/ToolAiToolProvider.java ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImpl.java ai-platform-tool/src/test/java/com/example/aichat/tool/ai/ToolAiToolProviderTest.java ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolExecutionServiceImplTest.java
```

确认没有残留的异步 `StpUtil.switchTo()`、没有移除 `tool:execute` 校验，且日志始终使用显式 `userId`。
