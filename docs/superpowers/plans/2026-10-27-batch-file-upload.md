# 批量文件上传接口实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在保留 `POST /api/files` 单文件接口的前提下，新增 `POST /api/files/batch`，支持最多 10 个文件的全量成功或全量失败上传。

**Architecture:** Controller 在 `/batch` 接收多个同名 `files` multipart 字段并返回元数据列表。`FileServiceImpl` 先完成整个批次的校验，再按输入顺序写入对象存储和元数据；失败时补偿删除本批次已写入的对象和元数据，同时保留原始异常。单文件 `upload(MultipartFile)` 的行为和契约不变。

**Tech Stack:** Java 17、Spring Boot 3.5、Spring MVC multipart、MyBatis-Plus、JUnit 5、Mockito、MockMvc。

---

### Task 1: 添加批量服务契约与失败场景测试

**Files:**
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java:10-12`
- Modify: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java:1-164`

- [ ] **Step 1: 扩展服务接口**

在 `FileService` 增加以下 import 和方法，不修改已有单文件方法：

```java
import java.util.List;

List<FileMetadataVO> uploadBatch(List<MultipartFile> files);
```

方法应紧邻现有的 `FileMetadataVO upload(MultipartFile file)` 声明。

- [ ] **Step 2: 写批量成功和数量校验测试**

在 `FileServiceImplTest` 增加 `java.util.List`、`verifyNoInteractions`、`times` 和 `eq` 的静态 import，并添加以下测试。测试使用现有 `setUp` 中的 `text/plain` 和 10 字节限制：

```java
@Test
void uploadsBatchInInputOrder() {
    MockMultipartFile first = new MockMultipartFile("files", "first.txt", "text/plain", "one".getBytes());
    MockMultipartFile second = new MockMultipartFile("files", "second.txt", "text/plain", "two".getBytes());

    List<com.example.aichat.file.vo.FileMetadataVO> result = service.uploadBatch(List.of(first, second));

    assertThat(result).hasSize(2);
    verify(storage, times(2)).put(eq("files"), any(), any(), any(Long.class), eq("text/plain"));
    verify(mapper, times(2)).insert(any(FileMetadata.class));
}

@Test
void rejectsBatchWithMoreThanMaximumFileCount() {
    MockMultipartFile file = new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes());

    assertThatThrownBy(() -> service.uploadBatch(java.util.Collections.nCopies(11, file)))
            .hasMessage("Too many files");

    verifyNoInteractions(storage, mapper);
}

@Test
void rejectsBatchWhenOneFileIsInvalidBeforeWritingAnything() {
    MockMultipartFile valid = new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes());
    MockMultipartFile invalid = new MockMultipartFile("files", "a.pdf", "application/pdf", "bad".getBytes());

    assertThatThrownBy(() -> service.uploadBatch(List.of(valid, invalid)))
            .hasMessage("File type is not allowed");

    verifyNoInteractions(storage, mapper);
}
```

The fully qualified VO reference may be replaced with the existing `FileMetadataVO` import when adding the test import; do not introduce a new production DTO.

- [ ] **Step 3: Write the compensation test for a later failure**

Add this test to make the all-or-nothing behavior explicit. The first insert succeeds and assigns an ID; the second insert throws, so the first metadata row and both uploaded objects must be cleaned up:

```java
@Test
void cleansPreviouslyUploadedObjectsAndMetadataWhenLaterFileFails() {
    MockMultipartFile first = new MockMultipartFile("files", "first.txt", "text/plain", "one".getBytes());
    MockMultipartFile second = new MockMultipartFile("files", "second.txt", "text/plain", "two".getBytes());
    java.util.concurrent.atomic.AtomicInteger insertCount = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(invocation -> {
        FileMetadata metadata = invocation.getArgument(0);
        if (insertCount.incrementAndGet() == 2) {
            throw new IllegalStateException("database failure");
        }
        metadata.id = 1L;
        return 1;
    }).when(mapper).insert(any(FileMetadata.class));

    assertThatThrownBy(() -> service.uploadBatch(List.of(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database failure");

    verify(mapper).deleteById(1L);
    verify(storage, times(2)).delete(eq("files"), any());
}
```

Add `doAnswer` to the existing Mockito static imports. The test intentionally checks cleanup without depending on generated UUID object keys.

- [ ] **Step 4: Run the focused service tests and verify they fail for the missing implementation**

Run:

```bash
mvn -pl ai-platform-file -Dtest=FileServiceImplTest -DfailIfNoTests=false test
```

Expected: compilation or test failure because `FileServiceImpl` does not yet implement `uploadBatch(List<MultipartFile>)`; no production behavior should be changed in this task.

---

### Task 2: Implement batch orchestration and compensation

**Files:**
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java:24-86, 182-212, 287-305`
- Test: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java`

- [ ] **Step 1: Add the batch method with pre-validation and ordered writes**

Add `java.util.List` support if needed (it already exists), then implement this method after the existing single-file `upload` method:

```java
@Override
public List<FileMetadataVO> uploadBatch(List<MultipartFile> files) {
    CurrentUser user = currentUserProvider.requireCurrentUser();
    if (files == null || files.isEmpty()) throw error(FileErrorCode.FILE_EMPTY);
    if (files.size() > MAX_FILE_COUNT) throw error(FileErrorCode.FILE_COUNT_EXCEEDED);

    List<FileMetadata> metadataList = files.stream()
            .map(file -> prepareMetadata(user, file))
            .toList();
    List<FileMetadata> uploaded = new ArrayList<>(metadataList.size());
    List<FileMetadata> inserted = new ArrayList<>(metadataList.size());
    try {
        for (int index = 0; index < files.size(); index++) {
            FileMetadata metadata = metadataList.get(index);
            putObject(files.get(index), metadata);
            uploaded.add(metadata);
            mapper.insert(metadata);
            inserted.add(metadata);
        }
    } catch (RuntimeException exception) {
        cleanupBatch(uploaded, inserted);
        throw exception;
    }
    return inserted.stream().map(this::toVO).toList();
}
```

The `files.stream()` preparation is deliberately before the `try` write section: a validation or filename failure must not trigger storage operations or cleanup.

- [ ] **Step 2: Extract shared metadata preparation without changing single-file rules**

Extract the existing validation, safe original-name, 255-character limit, object-key, and metadata field assignments into this helper:

```java
private FileMetadata prepareMetadata(CurrentUser user, MultipartFile file) {
    validateUpload(file);
    String originalName = safeOriginalName(file.getOriginalFilename());
    if (originalName.length() > 255) throw error(FileErrorCode.FILE_NAME_TOO_LONG);

    FileMetadata metadata = new FileMetadata();
    metadata.userId = user.id();
    metadata.bucketName = properties.getBucket();
    metadata.objectKey = objectKey(user.id(), originalName);
    metadata.originalName = originalName;
    metadata.contentType = file.getContentType();
    metadata.fileSize = file.getSize();
    metadata.status = FileStatus.ACTIVE;
    metadata.deleted = 0;
    return metadata;
}
```

Update the existing `upload(MultipartFile)` to use `prepareMetadata` and a shared `putObject` helper, while preserving its current behavior: storage-read `IOException` becomes `FILE_STORAGE_ERROR`; a metadata insert failure deletes the current object and rethrows the database exception.

- [ ] **Step 3: Extract object storage write and implement batch cleanup**

Use this helper for the existing and batch paths so the input stream handling remains identical:

```java
private void putObject(MultipartFile file, FileMetadata metadata) {
    try (InputStream inputStream = file.getInputStream()) {
        storage.put(metadata.bucketName, metadata.objectKey, inputStream,
                file.getSize(), file.getContentType());
    } catch (IOException exception) {
        throw new BusinessException(FileErrorCode.FILE_STORAGE_ERROR.errorCode(),
                "Unable to read uploaded file");
    }
}
```

Add compensation that ignores cleanup exceptions and never replaces the original exception:

```java
private void cleanupBatch(List<FileMetadata> uploaded, List<FileMetadata> inserted) {
    for (FileMetadata metadata : inserted) {
        try {
            mapper.deleteById(metadata.id);
        } catch (RuntimeException ignored) {
        }
    }
    for (FileMetadata metadata : uploaded) {
        try {
            storage.delete(metadata.bucketName, metadata.objectKey);
        } catch (RuntimeException ignored) {
        }
    }
}
```

For the single-file path, retain its current insert-failure cleanup behavior rather than routing it through batch compensation; this keeps the old endpoint's error behavior unchanged.

- [ ] **Step 4: Run the focused service tests and verify they pass**

Run:

```bash
mvn -pl ai-platform-file -Dtest=FileServiceImplTest -DfailIfNoTests=false test
```

Expected: all `FileServiceImplTest` tests pass, including the existing single-file validation and cleanup tests plus the new batch tests.

---

### Task 3: Add the `/batch` controller endpoint

**Files:**
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java:1-35`
- Modify: `ai-platform-file/src/test/java/com/example/aichat/file/controller/FileControllerTest.java:1-50`

- [ ] **Step 1: Write the failing MockMvc contract test**

Add `java.util.List` and the `FileMetadataVO` import if needed, then add this test next to the existing single-file upload test:

```java
@Test
void uploadsMultipleMultipartFiles() throws Exception {
    when(service.uploadBatch(any())).thenReturn(List.of(
            new FileMetadataVO(1L, 100L, "a.txt", "text/plain", 5L, null, null, null),
            new FileMetadataVO(2L, 100L, "b.txt", "text/plain", 5L, null, null, null)));

    mockMvc.perform(multipart("/api/files/batch")
                    .file(new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes()))
                    .file(new MockMultipartFile("files", "b.txt", "text/plain", "world".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data[0].id").value(1))
            .andExpect(jsonPath("$.data[1].id").value(2));
}
```

Run:

```bash
mvn -pl ai-platform-file -Dtest=FileControllerTest -DfailIfNoTests=false test
```

Expected: compilation or request-mapping failure because `/api/files/batch` and `uploadBatch` do not exist yet.

- [ ] **Step 2: Add the endpoint without changing the existing endpoint**

Import `java.util.List` and add this method after the existing single-file `upload` method:

```java
@PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ApiResponse<List<FileMetadataVO>> uploadBatch(@RequestPart("files") List<MultipartFile> files) {
    return ApiResponse.success(service.uploadBatch(files));
}
```

Do not rename or alter the existing `@PostMapping` method, whose field remains `file` and whose response remains a single `FileMetadataVO`.

- [ ] **Step 3: Run both controller tests**

Run:

```bash
mvn -pl ai-platform-file -Dtest=FileControllerTest -DfailIfNoTests=false test
```

Expected: both `uploadsMultipartFile` and `uploadsMultipleMultipartFiles` pass, along with the existing download test.

---

### Task 4: Run the module regression suite and review the final diff

**Files:**
- Verify: `ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java`
- Verify: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java`
- Verify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java`
- Verify: `ai-platform-file/src/test/java/com/example/aichat/file/controller/FileControllerTest.java`
- Verify: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java`

- [ ] **Step 1: Run the complete file module test suite**

Run:

```bash
mvn -pl ai-platform-file test
```

Expected: the module's controller, service, and storage tests pass with zero failures and zero errors.

- [ ] **Step 2: Check the patch for accidental scope changes**

Run:

```bash
git diff --check
git status --short
git diff -- ai-platform-file/src/main/java/com/example/aichat/file/controller/FileController.java ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java ai-platform-file/src/test/java/com/example/aichat/file/controller/FileControllerTest.java ai-platform-file/src/test/java/com/example/aichat/file/service/FileServiceImplTest.java
```

Confirm that only the new `/api/files/batch` contract, batch service orchestration/compensation, and focused tests changed. Do not stage or commit the pre-existing `.DS_Store` file or create a commit unless the user explicitly requests it.
