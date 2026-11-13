# ToolManagementView Auth Fields Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend `ToolManagementView` with persisted authentication metadata and return it consistently for manually managed HTTP tools and discovered MCP tools.

**Architecture:** Keep the existing record-based response model and the two existing local `toView` mapping methods. Add `ToolAuthType authType` and `String authHeaderName` after `remoteToolName`, then pass the corresponding values from `ToolDefinition` without introducing a new mapper or changing authentication behavior.

**Tech Stack:** Java record, Spring service layer, MyBatis-Plus entities, JUnit 5, Mockito, AssertJ, Maven.

---

## File map

- Modify `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolManagementView.java` — declare the two response fields and import `ToolAuthType`.
- Modify `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolManagementServiceImpl.java` — pass the HTTP tool entity's authentication fields into the view constructor.
- Modify `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImpl.java` — pass discovered MCP entity authentication fields into the view constructor.
- Modify `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolManagementServiceImplTest.java` — verify bearer and API key metadata returned by the HTTP management flow.
- Modify `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImplTest.java` — verify discovered MCP tools return `NONE` and no header name.

## Task 1: Add failing view-mapping assertions

**Files:**
- Modify: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolManagementServiceImplTest.java`
- Modify: `ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImplTest.java`

- [ ] **Step 1: Assert bearer metadata for the existing HTTP creation test.**

In `createsHttpToolWithEncryptedCredentials`, immediately after the existing `hasCredential` assertion, add:

```java
assertThat(result.authType()).isEqualTo(ToolAuthType.BEARER);
assertThat(result.authHeaderName()).isNull();
```

- [ ] **Step 2: Add an API key mapping test.**

Add this test to `ToolManagementServiceImplTest`:

```java
@Test
void exposesApiKeyAuthenticationConfiguration() {
    when(mapper.selectCount(any())).thenReturn(0L);
    when(urlSafetyValidator.validate("https://example.com/orders"))
            .thenReturn(URI.create("https://example.com/orders"));
    when(mapper.insert(any(ToolDefinition.class))).thenAnswer(invocation -> 1);

    var result = service.create(request(ToolAuthType.API_KEY, "X-Api-Key", "secret-token"));

    assertThat(result.authType()).isEqualTo(ToolAuthType.API_KEY);
    assertThat(result.authHeaderName()).isEqualTo("X-Api-Key");
}
```

Replace the existing helper with an overload so the existing bearer test remains unchanged while the new test can select API key authentication:

```java
private ToolUpsertRequest request(String credential) {
    return request(ToolAuthType.BEARER, null, credential);
}

private ToolUpsertRequest request(ToolAuthType authType, String authHeaderName, String credential) {
    return new ToolUpsertRequest(
            " orders ", "Orders", "Order lookup", ToolType.HTTP,
            new HttpToolConfig("https://example.com/orders", ToolHttpMethod.POST,
                    Map.of("Accept", "application/json"), Map.of(),
                    new ObjectMapper().createObjectNode().put("id", "{{orderId}}")),
            authType, authHeaderName, credential, Map.of("X-Tenant", "demo"), null);
}
```

- [ ] **Step 3: Assert discovered MCP authentication metadata.**

Add the `ToolAuthType` import to `ToolDiscoveryPersistenceServiceImplTest`, then append these assertions to `createsDisabledAvailableSnapshotAndMarksServerDiscoverySuccessful` after the existing snapshot assertions:

```java
assertThat(result.tools()).hasSize(1);
var view = result.tools().get(0);
assertThat(view.authType()).isEqualTo(ToolAuthType.NONE);
assertThat(view.authHeaderName()).isNull();
```

- [ ] **Step 4: Run the focused tests and confirm they fail before production changes.**

Run:

```bash
mvn -pl ai-platform-tool -am \
  -Dtest=ToolManagementServiceImplTest,ToolDiscoveryPersistenceServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: compilation fails because `ToolManagementView` does not yet declare `authType()` or `authHeaderName()` and its existing constructor shape does not yet include the new fields.

## Task 2: Add fields and update both production mappings

**Files:**
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolManagementView.java:3-26`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolManagementServiceImpl.java:226-231`
- Modify: `ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImpl.java:150-154`

- [ ] **Step 1: Add the authentication type import and record components.**

Update `ToolManagementView.java` to import `ToolAuthType` and use this component order:

```java
import com.example.aichat.tool.enums.ToolAuthType;
```

```java
Long mcpServerId,
String remoteToolName,
ToolAuthType authType,
String authHeaderName,
JsonNode httpConfig,
JsonNode inputSchema,
```

Keep all remaining components and their order unchanged.

- [ ] **Step 2: Pass HTTP entity authentication fields into the management view.**

In `ToolManagementServiceImpl.toView`, change the constructor argument sequence from:

```java
tool.getRemoteToolName(), httpConfig, parseSchema(tool.getInputSchema()),
```

to:

```java
tool.getRemoteToolName(), tool.getAuthType(), tool.getAuthHeaderName(),
httpConfig, parseSchema(tool.getInputSchema()),
```

Do not alter the existing `hasCredential` calculation or credential version mapping.

- [ ] **Step 3: Pass MCP entity authentication fields into the discovery view.**

In `ToolDiscoveryPersistenceServiceImpl.toView`, change the constructor arguments from:

```java
tool.getRemoteToolName(), null, parse(tool.getInputSchema()), false, tool.getCredentialVersion(),
```

to:

```java
tool.getRemoteToolName(), tool.getAuthType(), tool.getAuthHeaderName(),
null, parse(tool.getInputSchema()), false, tool.getCredentialVersion(),
```

This preserves `newSnapshot`'s existing `ToolAuthType.NONE` value and its `null` header name.

- [ ] **Step 4: Run the focused tests and verify the new mappings pass.**

Run:

```bash
mvn -pl ai-platform-tool -am \
  -Dtest=ToolManagementServiceImplTest,ToolDiscoveryPersistenceServiceImplTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected result: both focused test classes pass, including bearer, API key, and MCP assertions.

## Task 3: Verify all consumers and regression coverage

**Files:**
- Verify: all `ToolManagementView` constructor calls under `ai-platform-tool/src` and all tests under `ai-platform-tool/src/test`

- [ ] **Step 1: Confirm every constructor call uses the new component order.**

Run:

```bash
rg -n -C 3 "new ToolManagementView" ai-platform-tool/src
```

Expected result: only the two known mapping methods appear, and each passes `authType` followed by `authHeaderName` between `remoteToolName` and the HTTP/schema fields.

- [ ] **Step 2: Run the complete tool module test suite.**

Run:

```bash
mvn -pl ai-platform-tool -am test
```

Expected result: the Maven reactor completes successfully with all existing tool-module tests passing.

- [ ] **Step 3: Check the final diff for accidental changes.**

Run:

```bash
git diff --check
git diff -- ai-platform-tool/src/main ai-platform-tool/src/test
```

Expected result: no whitespace errors, and the diff contains only the view declaration, the two mappings, and the two service test updates.

- [ ] **Step 4: Commit the implementation.**

```bash
git add \
  ai-platform-tool/src/main/java/com/example/aichat/tool/dto/management/ToolManagementView.java \
  ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolManagementServiceImpl.java \
  ai-platform-tool/src/main/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImpl.java \
  ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolManagementServiceImplTest.java \
  ai-platform-tool/src/test/java/com/example/aichat/tool/service/impl/ToolDiscoveryPersistenceServiceImplTest.java

git commit -m "$(cat <<'EOF'
feat: expose tool authentication metadata

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
EOF
)"
```
