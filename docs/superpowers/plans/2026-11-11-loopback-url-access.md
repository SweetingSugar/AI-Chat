# Tool 回环地址访问实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 MCP Server 和 HTTP Tool 默认可以访问 `localhost`、`127.0.0.1`、`::1` 及其 IPv4-mapped loopback 地址，同时保留对任意本地地址、链路本地地址和其他私有网段的 SSRF 防护。

**Architecture:** 继续复用 `UrlSafetyValidator` 作为 MCP 与 HTTP URL 的统一入口，不增加配置项。调整地址分类：loopback 不再属于始终阻断地址，也不再被归类为需要 `allow-private-network` 才能访问的私有地址；any-local、link-local 和非回环私网保持原有规则。通过单元测试锁定放行与拒绝边界。

**Tech Stack:** Java 21, Spring Boot, JUnit 5, AssertJ, `InetAddress`, `URI`, Maven。

---

## 文件变更范围

- 修改：`ai-platform-tool/src/main/java/com/example/aichat/tool/validation/UrlSafetyValidator.java`
  - 调整 loopback、IPv4-mapped loopback 和 IPv4 私网分类。
  - 删除不再使用的 mapped-loopback 专用判断方法。
- 修改：`ai-platform-tool/src/test/java/com/example/aichat/tool/validation/UrlSafetyValidatorTest.java`
  - 将默认拒绝 loopback 的测试改为默认允许。
  - 增加 IPv6 loopback、IPv4-mapped loopback 和 any-local 的边界测试。
- 不修改：`ai-platform-boot/src/main/resources/application.yml`
  - 本需求选择默认放行 loopback，不新增配置开关。
- 不修改：MCP/HTTP 执行器、数据库迁移和 API DTO。

## Task 1: 先锁定回环地址的新行为

**Files:**
- Modify: `ai-platform-tool/src/test/java/com/example/aichat/tool/validation/UrlSafetyValidatorTest.java:10-51`

- [ ] **Step 1: 将默认 loopback 拒绝测试改为放行测试**

将现有 `rejectsLoopbackAddressesByDefault` 替换为以下测试，覆盖 IPv4、主机名和 IPv6 loopback：

```java
@Test
void permitsLoopbackAddressesByDefault() {
    UrlSafetyValidator validator = new UrlSafetyValidator(properties(false));

    assertThat(validator.validate("http://127.0.0.1/api").getHost()).isEqualTo("127.0.0.1");
    assertThat(validator.validate("http://localhost/api").getHost()).isEqualTo("localhost");
    assertThat(validator.validate("http://[::1]/api").getHost()).isEqualTo("[::1]");
}
```

- [ ] **Step 2: 增加 IPv4-mapped loopback 放行测试**

在 `UrlSafetyValidatorTest` 中增加：

```java
@Test
void permitsIpv4MappedLoopbackByDefault() {
    UrlSafetyValidator validator = new UrlSafetyValidator(properties(false));

    assertThat(validator.validate("http://[::ffff:127.0.0.1]/api").getHost())
            .isEqualTo("[::ffff:127.0.0.1]");
}
```

- [ ] **Step 3: 增加 any-local 地址仍被拒绝的测试**

增加以下测试，确保放开 loopback 不会误放开 `0.0.0.0` 和 `::`：

```java
@Test
void rejectsAnyLocalAddresses() {
    UrlSafetyValidator validator = new UrlSafetyValidator(properties(false));

    assertThatThrownBy(() -> validator.validate("http://0.0.0.0/api"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).errorCode())
            .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
    assertThatThrownBy(() -> validator.validate("http://[::]/api"))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).errorCode())
            .isEqualTo(com.example.aichat.common.error.ErrorCode.TOOL_CONFIG_INVALID);
}
```

- [ ] **Step 4: 运行新增测试，确认它们在实现前失败**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=UrlSafetyValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：失败，原因是当前校验器仍将 loopback 和 IPv4-mapped loopback 判定为受限地址；现有私网、协议和 URL 用户信息测试仍应保持通过。

## Task 2: 调整 URL 安全校验分类

**Files:**
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/validation/UrlSafetyValidator.java:51-89`

- [ ] **Step 1: 仅保留 any-local 和 link-local 的始终阻断规则**

将 `isAlwaysBlocked` 改为：

```java
private boolean isAlwaysBlocked(InetAddress address) {
    return address.isAnyLocalAddress()
            || address.isLinkLocalAddress();
}
```

这样 `127.0.0.1`、`::1` 和 IPv4-mapped loopback 不会在第一层被阻断，但 `0.0.0.0`、`::` 和链路本地地址仍会被阻断。

- [ ] **Step 2: 从私网判断中移除 IPv4 loopback 网段判断**

在 `isPrivateNetwork` 的 IPv4 分支中删除 `first == 127 ||`，保留其他网段：

```java
return first == 0
        || first == 10
        || (first == 172 && second >= 16 && second <= 31)
        || (first == 192 && second == 168)
        || (first == 100 && second >= 64 && second <= 127)
        || (first == 169 && second == 254)
        || (first == 198 && (second == 18 || second == 19));
```

`first == 0` 仍然只覆盖 IPv4 unspecified/特殊地址；`127.x.x.x` 不再被作为私网阻断，而是由 loopback 语义允许访问。其他私网判断及 `allow-private-network` 分支不变。

- [ ] **Step 3: 删除不再使用的 `isMappedLoopback` 方法**

由于 IPv4-mapped loopback 不再被 `isAlwaysBlocked` 调用，删除 `isMappedLoopback` 方法及对应的 `Inet6Address` import；保留 `InetAddress`、`URI` 和现有 URL 解析逻辑。

- [ ] **Step 4: 运行定向测试，确认实现通过**

运行：

```bash
mvn -pl ai-platform-tool -am -Dtest=UrlSafetyValidatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`UrlSafetyValidatorTest` 全部通过，包括 loopback 放行、any-local 拒绝、私网开关、非 HTTP(S) 和 URL 用户信息校验。

## Task 3: 执行模块回归验证

**Files:**
- No additional files.

- [ ] **Step 1: 检查变更范围和空白错误**

运行：

```bash
git diff --check
git status --short
```

预期：只有 `UrlSafetyValidator.java` 和 `UrlSafetyValidatorTest.java` 的目标修改；不修改或覆盖工作区中已有的 `.DS_Store` 和其他无关文件。

- [ ] **Step 2: 运行整个 tool 模块测试**

运行：

```bash
mvn -pl ai-platform-tool -am test
```

预期：tool 模块及其依赖模块测试全部通过。

- [ ] **Step 3: 核对最终行为**

确认以下行为：

- `http://localhost:端口`、`http://127.0.0.1:端口` 和 `http://[::1]:端口` 可以通过 URL 校验；
- `http://0.0.0.0:端口` 和 `http://[::]:端口` 仍被拒绝；
- `10.x.x.x`、`172.16.x.x`、`192.168.x.x` 在 `allow-private-network=false` 时仍被拒绝；
- `allow-private-network=true` 时既有私网放行行为保持不变；
- MCP 与 HTTP Tool 因共用 `UrlSafetyValidator` 同时获得 loopback 访问能力。

- [ ] **Step 4: 汇报验证结果**

最终说明修改文件、loopback 放行范围、仍然保留的 SSRF 防护，以及 Maven 测试命令的实际结果。除非用户明确要求，不创建 git commit。
