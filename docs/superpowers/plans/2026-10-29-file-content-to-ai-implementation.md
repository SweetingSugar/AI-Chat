# File Content to AI Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make TXT, PDF, and image attachments available to the selected AI model while preserving the existing `fileIds` chat API and multi-turn attachment behavior.

**Architecture:** The file module validates ownership, reads private MinIO objects, and converts TXT/PDF to text while preserving image bytes. The chat module resolves attachment IDs from each historical message and maps file content into provider-neutral AI content DTOs. The AI module converts those DTOs to LangChain4j text/image content, checks per-model vision capability, and keeps existing provider adapters and text-only behavior compatible.

**Tech Stack:** Java 17, Spring Boot 3.5.16, Maven, MyBatis-Plus, MinIO 8.6.0, Apache PDFBox 3.0.5, LangChain4j 1.21.0, Reactor, JUnit 5, Mockito, AssertJ.

---

## File map

### New files

- `ai-platform-file/src/main/java/com/example/aichat/file/content/FileContent.java` — immutable normalized content returned by the file module.
- `ai-platform-file/src/main/java/com/example/aichat/file/service/FileContentService.java` — explicit internal boundary for reading owned active files.
- `ai-platform-file/src/main/java/com/example/aichat/file/content/PdfTextExtractor.java` — PDF-only text extraction using PDFBox.
- `ai-platform-file/src/test/java/com/example/aichat/file/content/PdfTextExtractorTest.java` — valid, empty, and malformed PDF cases.
- `ai-platform-file/src/test/java/com/example/aichat/file/service/FileContentServiceTest.java` — ownership, storage reads, MIME dispatch, and ordering.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageContentType.java` — TEXT and IMAGE content variants.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageContentDTO.java` — provider-neutral text/image payload.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/core/AiModelCapabilityValidator.java` — validates image input against the routed model.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/context/MessageConverterTest.java` — text and Base64 image conversion.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/context/TokenBudgetManagerTest.java` — attachment-aware context trimming.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/usage/TokenUsageCalculatorTest.java` — attachment-aware usage estimates.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/core/AiModelCapabilityValidatorTest.java` — vision capability acceptance/rejection.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java` — persistence and DTO mapping of `supportVision`.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OpenAiModelProviderTest.java` — OpenAI-compatible image payload.
- `ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatMessageServiceImplTest.java` — historical attachment IDs in context.
- `ai-platform-chat/src/test/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImplTest.java` — attachment resolution and AI request mapping.

### Modified files

- `pom.xml` — manage the PDFBox version.
- `ai-platform-file/pom.xml` — add PDFBox.
- `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java` — expose the new content-reading boundary through the existing service bean.
- `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java` — inject `PdfTextExtractor`, read objects, validate ownership, and dispatch content types.
- `ai-platform-file/src/main/resources/db/migration/V9__ai_model_vision.sql` — add `ai_model.support_vision`.
- `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatMessagePair.java` — carry ordered file IDs.
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/ChatMessageFileService.java` — expose ordered file IDs per message.
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageFileServiceImpl.java` — implement attachment ID lookup.
- `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageServiceImpl.java` — add file IDs to context pairs.
- `ai-platform-chat/src/main/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImpl.java` — batch-read and map file content into AI messages.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageDTO.java` — add optional content blocks while retaining the two-argument constructor.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/context/MessageConverter.java` — convert images to LangChain4j Base64 image content.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/context/TokenBudgetManager.java` — count attachment costs and reject an oversized current message.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/usage/TokenUsageCalculator.java` — include normalized attachment estimates without logging bytes.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/core/DefaultAiChatService.java` — invoke model capability validation before provider calls.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiModel.java` — add `supportVision`.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiModelUpsertRequest.java` — accept a validated vision flag.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiModelDTO.java` — expose vision capability to available-model clients.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiModelManagementDTO.java` — expose vision capability to administrators.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/AiModelConfigSnapshot.java` — carry the routed vision flag.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java` — copy and map the flag.
- `ai-platform-ai/src/main/java/com/example/aichat/ai/core/AiRequestValidator.java` — validate new content blocks.
- `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java` — add safe AI attachment error codes.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/AiModelFactoryTest.java` — update snapshot construction.
- `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java` — verify image content reaches the Ollama request.
- `docs/backend-development-guide.md` — document file-to-model behavior and model capability.
- `docs/frontend-api-integration.md` — document `fileIds` multi-turn behavior and image-model requirements.

---

### Task 1: Add the secure file-content reader

**Files:**
- Modify: `pom.xml:19-29`
- Modify: `ai-platform-file/pom.xml:12-48`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/content/FileContent.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileContentService.java`
- Create: `ai-platform-file/src/main/java/com/example/aichat/file/content/PdfTextExtractor.java`
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/FileService.java:12-30`
- Modify: `ai-platform-file/src/main/java/com/example/aichat/file/service/impl/FileServiceImpl.java:35-190`
- Create: `ai-platform-file/src/test/java/com/example/aichat/file/content/PdfTextExtractorTest.java`
- Create: `ai-platform-file/src/test/java/com/example/aichat/file/service/FileContentServiceTest.java`

- [ ] **Step 1: Add the PDFBox dependency and content types**

Add a parent property and dependency management entry:

```xml
<properties>
    <pdfbox.version>3.0.5</pdfbox.version>
</properties>
```

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>${pdfbox.version}</version>
</dependency>
```

Add the same dependency to `ai-platform-file/pom.xml` without a version.

Create the normalized value object:

```java
package com.example.aichat.file.content;

public record FileContent(Long fileId, String fileName, String contentType,
                          String text, byte[] bytes) {
    public FileContent {
        bytes = bytes == null ? null : bytes.clone();
    }
}
```

Create the internal service boundary:

```java
package com.example.aichat.file.service;

import com.example.aichat.file.content.FileContent;

import java.util.List;

public interface FileContentService {
    List<FileContent> readOwnedActive(Long userId, List<Long> fileIds);
}
```

Add `FileContentService` as a parent interface of `FileService` so the existing `FileServiceImpl` bean implements both interfaces.

- [ ] **Step 2: Write parser tests and run them to verify failure**

Create `PdfTextExtractorTest` with these cases:

```java
@Test
void extractsTextFromPdf() throws IOException {
    byte[] pdf = pdfWithText("invoice total 42");

    assertThat(extractor.extract(pdf)).contains("invoice total 42");
}

@Test
void rejectsPdfWithoutExtractedText() {
    assertThatThrownBy(() -> extractor.extract(pdfWithoutText()))
            .hasMessage("File content is empty");
}

@Test
void rejectsMalformedPdf() {
    assertThatThrownBy(() -> extractor.extract("not pdf".getBytes(StandardCharsets.UTF_8)))
            .hasMessage("Unable to parse file content");
}
```

Build the PDF fixture with PDFBox in the test using `PDDocument`, `PDPage`, `PDPageContentStream`, and `Standard14Fonts.FontName.HELVETICA`; do not add a binary fixture to the repository. Run:

```bash
mvn -pl ai-platform-file -am -Dtest=PdfTextExtractorTest test
```

Expected: compilation or test failure because `PdfTextExtractor` does not exist yet.

- [ ] **Step 3: Implement the PDF extractor**

Create `PdfTextExtractor` with this behavior:

```java
package com.example.aichat.file.content;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PdfTextExtractor {
    public String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ErrorCode.AI_FILE_CONTENT_EMPTY, "File content is empty");
            }
            return text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AI_FILE_PARSE_FAILED, "Unable to parse file content", exception);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.AI_FILE_PARSE_FAILED, "Unable to parse file content", exception);
        }
    }
}
```

- [ ] **Step 4: Write file-service content tests and run them to verify failure**

Create `FileContentServiceTest` with mocked `FileMetadataMapper` and `ObjectStorage`. Cover:

```java
@Test
void readsTextFileUsingOwnedMetadataAndStorageObject() {
    FileMetadata metadata = metadata(100L, 10L, "notes.txt", "text/plain");
    when(mapper.selectBatchIds(List.of(10L))).thenReturn(List.of(metadata));
    when(storage.get("files", "users/100/notes.txt"))
            .thenReturn(new ByteArrayInputStream("hello file".getBytes(StandardCharsets.UTF_8)));

    List<FileContent> result = service.readOwnedActive(100L, List.of(10L));

    assertThat(result).singleElement().satisfies(content -> {
        assertThat(content.text()).isEqualTo("hello file");
        assertThat(content.bytes()).isNull();
    });
    verify(storage).get("files", "users/100/notes.txt");
}

@Test
void readsImageBytesWithoutExposingStorageCoordinates() {
    FileMetadata metadata = metadata(100L, 11L, "image.png", "image/png");
    when(mapper.selectBatchIds(List.of(11L))).thenReturn(List.of(metadata));
    when(storage.get("files", "users/100/image.png"))
            .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

    FileContent content = service.readOwnedActive(100L, List.of(11L)).get(0);

    assertThat(content.contentType()).isEqualTo("image/png");
    assertThat(content.bytes()).containsExactly(1, 2, 3);
    assertThat(content.text()).isNull();
    verify(storage).get("files", "users/100/image.png");
}

@Test
void preservesRequestedFileOrder() {
    FileMetadata first = metadata(100L, 11L, "first.txt", "text/plain");
    FileMetadata second = metadata(100L, 10L, "second.txt", "text/plain");
    when(mapper.selectBatchIds(List.of(11L, 10L))).thenReturn(List.of(second, first));
    when(storage.get(any(), eq("users/100/first.txt")))
            .thenReturn(new ByteArrayInputStream("first".getBytes(StandardCharsets.UTF_8)));
    when(storage.get(any(), eq("users/100/second.txt")))
            .thenReturn(new ByteArrayInputStream("second".getBytes(StandardCharsets.UTF_8)));

    assertThat(service.readOwnedActive(100L, List.of(11L, 10L)))
            .extracting(FileContent::fileId)
            .containsExactly(11L, 10L);
}

@Test
void rejectsForeignInactiveAndUnsupportedFilesBeforeReadingStorage() {
    FileMetadata foreign = metadata(200L, 12L, "foreign.txt", "text/plain");
    when(mapper.selectBatchIds(List.of(12L))).thenReturn(List.of(foreign));
    assertThatThrownBy(() -> service.readOwnedActive(100L, List.of(12L)))
            .hasMessage("File access denied");

    FileMetadata deleted = metadata(100L, 13L, "deleted.txt", "text/plain");
    deleted.status = FileStatus.DELETED;
    when(mapper.selectBatchIds(List.of(13L))).thenReturn(List.of(deleted));
    assertThatThrownBy(() -> service.readOwnedActive(100L, List.of(13L)))
            .hasMessage("File not found");

    FileMetadata word = metadata(100L, 14L, "file.doc", "application/msword");
    when(mapper.selectBatchIds(List.of(14L))).thenReturn(List.of(word));
    assertThatThrownBy(() -> service.readOwnedActive(100L, List.of(14L)))
            .hasMessage("File type is not supported for AI analysis");

    verify(storage, never()).get(any(), any());
}

private FileMetadata metadata(Long userId, Long id, String name, String contentType) {
    FileMetadata metadata = new FileMetadata();
    metadata.id = id;
    metadata.userId = userId;
    metadata.bucketName = "files";
    metadata.objectKey = "users/" + userId + "/" + name;
    metadata.originalName = name;
    metadata.contentType = contentType;
    metadata.fileSize = 1L;
    metadata.status = FileStatus.ACTIVE;
    return metadata;
}
```

Run:

```bash
mvn -pl ai-platform-file -am -Dtest=FileContentServiceTest test
```

Expected: failure because `readOwnedActive` is not implemented.

- [ ] **Step 5: Implement content dispatch in `FileServiceImpl`**

Implement `readOwnedActive` by reusing the existing `uniqueIds`, `byIds`, `requireOwner`, and `requireActive` methods. Read each object through `ObjectStorage.get`, close the stream, then dispatch:

```java
@Override
public List<FileContent> readOwnedActive(Long userId, List<Long> fileIds) {
    List<Long> ids = uniqueIds(fileIds);
    if (ids.isEmpty()) return List.of();
    Map<Long, FileMetadata> records = byIds(ids);
    List<FileContent> result = new ArrayList<>(ids.size());
    for (Long fileId : ids) {
        FileMetadata metadata = records.get(fileId);
        if (metadata == null) throw error(FileErrorCode.FILE_NOT_FOUND);
        requireOwner(metadata, userId);
        requireActive(metadata);
        byte[] bytes;
        try (InputStream input = storage.get(metadata.bucketName, metadata.objectKey)) {
            bytes = input.readAllBytes();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to read file content", exception);
        }
        result.add(toFileContent(metadata, bytes));
    }
    return result;
}
```

`toFileContent` must support exactly `text/plain`, `application/pdf`, `image/jpeg`, `image/png`, and `image/gif`; use UTF-8 for text, `PdfTextExtractor` for PDF, and raw bytes for images. For any other MIME type, throw `new BusinessException(ErrorCode.AI_FILE_TYPE_NOT_SUPPORTED)` directly; do not add a second file-module error code for the same AI boundary.

Run both file test classes and the existing file suite:

```bash
mvn -pl ai-platform-file -am -Dtest=PdfTextExtractorTest,FileContentServiceTest,FileServiceImplTest test
```

Expected: PASS.

- [ ] **Step 6: Commit the file reader**

```bash
git add pom.xml ai-platform-file/pom.xml ai-platform-file/src/main/java ai-platform-file/src/test/java
git commit -m "feat: add secure AI file content reader"
```

---

### Task 2: Add provider-neutral text and image message content

**Files:**
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageContentType.java`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageContentDTO.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiMessageDTO.java:1-5`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/context/MessageConverter.java:1-22`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/context/MessageConverterTest.java`

- [ ] **Step 1: Write converter tests and run them to verify failure**

Test that the legacy constructor still produces one text message and that a message with an image produces a `UserMessage` with text and `ImageContent`:

```java
@Test
void convertsImageBytesToBase64ImageContent() {
    AiMessageDTO message = new AiMessageDTO(
            AiMessageRole.USER,
            "describe it",
            List.of(AiMessageContentDTO.image("image/png", new byte[]{1, 2, 3})));

    UserMessage user = (UserMessage) MessageConverter.toLangChain4j(List.of(message)).get(0);

    assertThat(user.contents()).hasSize(2);
    assertThat(user.contents().get(0)).isInstanceOf(TextContent.class);
    ImageContent imageContent = (ImageContent) user.contents().get(1);
    assertThat(imageContent.image().mimeType()).isEqualTo("image/png");
    assertThat(imageContent.image().base64Data())
            .isEqualTo(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}));
}
```

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=MessageConverterTest test
```

Expected: compilation failure because the content DTO and three-argument constructor do not exist.

- [ ] **Step 2: Implement the content DTOs and backwards-compatible message DTO**

Use these types:

```java
public enum AiMessageContentType {
    TEXT, IMAGE
}
```

```java
public record AiMessageContentDTO(AiMessageContentType type, String text,
                                  String mediaType, byte[] data) {
    public AiMessageContentDTO {
        if (type == null) throw new IllegalArgumentException("Content type must not be null");
        data = data == null ? null : data.clone();
    }

    public static AiMessageContentDTO text(String value) {
        return new AiMessageContentDTO(AiMessageContentType.TEXT, value, null, null);
    }

    public static AiMessageContentDTO image(String mediaType, byte[] data) {
        return new AiMessageContentDTO(AiMessageContentType.IMAGE, null, mediaType, data);
    }
}
```

```java
public record AiMessageDTO(AiMessageRole role, String content,
                           List<AiMessageContentDTO> contents) {
    public AiMessageDTO(AiMessageRole role, String content) {
        this(role, content, List.of());
    }

    public AiMessageDTO {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }

    public boolean hasImageContent() {
        return contents.stream().anyMatch(item -> item.type() == AiMessageContentType.IMAGE);
    }

    public int estimatedInputTokens() {
        int textTokens = content == null ? 0 : (content.length() + 3) / 4;
        return contents.stream().mapToInt(item -> item.type() == AiMessageContentType.IMAGE
                ? 1024 : item.text() == null ? 0 : (item.text().length() + 3) / 4).sum() + textTokens;
    }
}
```

- [ ] **Step 3: Implement LangChain4j conversion**

Update `MessageConverter` to append `TextContent` for the message text and TEXT blocks, and to construct Base64 image blocks without URLs:

```java
private static ChatMessage userMessage(AiMessageDTO message) {
    List<Content> contents = new ArrayList<>();
    if (message.content() != null && !message.content().isBlank()) {
        contents.add(TextContent.from(message.content()));
    }
    for (AiMessageContentDTO content : message.contents()) {
        if (content.type() == AiMessageContentType.TEXT) {
            contents.add(TextContent.from(content.text()));
        } else {
            Image image = Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(content.data()))
                    .mimeType(content.mediaType())
                    .build();
            contents.add(ImageContent.from(image));
        }
    }
    return UserMessage.from(contents);
}
```

Keep SYSTEM and ASSISTANT as their existing string conversions. Route USER through `userMessage(message)`.

- [ ] **Step 4: Run the AI converter suite and commit**

```bash
mvn -pl ai-platform-ai -am -Dtest=MessageConverterTest test
```

Expected: PASS.

```bash
git add ai-platform-ai/src/main/java ai-platform-ai/src/test/java
git commit -m "feat: represent text and image AI content"
```

---

### Task 3: Preserve attachment IDs in chat context

**Files:**
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/model/ChatMessagePair.java:1-6`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/ChatMessageFileService.java:8-12`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageFileServiceImpl.java:41-57`
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/service/impl/ChatMessageServiceImpl.java:141-145`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/service/impl/ChatMessageServiceImplTest.java`
- Modify: `ai-platform-chat/src/test/java/com/example/aichat/chat/service/ChatMessageFileServiceTest.java`

- [ ] **Step 1: Add the failing ordered-ID service test**

Add this contract to `ChatMessageFileService`:

```java
Map<Long, List<Long>> findFileIdsByMessageIds(List<Long> messageIds);
```

Test that mapper order is preserved:

```java
@Test
void returnsFileIdsGroupedInRelationOrder() {
    ChatMessageFile first = relation(20L, 11L, 0);
    ChatMessageFile second = relation(20L, 10L, 1);
    when(mapper.selectByMessageIds(List.of(20L))).thenReturn(List.of(first, second));

    assertThat(service.findFileIdsByMessageIds(List.of(20L)))
            .containsEntry(20L, List.of(11L, 10L));
}
```

Run:

```bash
mvn -pl ai-platform-chat -am -Dtest=ChatMessageFileServiceTest test
```

Expected: compilation failure until the new method is implemented.

- [ ] **Step 2: Implement grouped ID lookup and extend `ChatMessagePair`**

Use a `LinkedHashMap` initialized for every requested message ID, then append mapper rows in their existing `sort_order, id` order. Return `Map.of()` for null or empty input.

Change the pair to:

```java
public record ChatMessagePair(ChatMessageRole role, String content, List<Long> fileIds) {
    public ChatMessagePair {
        fileIds = fileIds == null ? List.of() : List.copyOf(fileIds);
    }
}
```

- [ ] **Step 3: Add the context test and update `ChatMessageServiceImpl`**

Mock `messageMapper.selectHistory(...)` with one user message and one completed assistant message; mock `findFileIdsByMessageIds` to return the user message’s IDs. Assert `context(...)` returns the user pair with the IDs in order and the assistant pair with an empty list.

Update `context(...)` to first filter the history, collect message IDs, query `findFileIdsByMessageIds`, and construct `ChatMessagePair(message.role, message.content, fileIds.getOrDefault(message.id, List.of()))`.

Run:

```bash
mvn -pl ai-platform-chat -am -Dtest=ChatMessageFileServiceTest,ChatMessageServiceImplTest test
```

Expected: PASS.

- [ ] **Step 4: Commit chat context support**

```bash
git add ai-platform-chat/src/main/java ai-platform-chat/src/test/java
git commit -m "feat: restore attachment IDs in chat context"
```

---

### Task 4: Map resolved file content into AI requests

**Files:**
- Modify: `ai-platform-chat/src/main/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImpl.java:18-38`
- Create: `ai-platform-chat/src/test/java/com/example/aichat/chat/gateway/impl/AiChatGatewayImplTest.java`

- [ ] **Step 1: Write the failing gateway test**

Mock `AiChatService` and `FileContentService`. Provide two chat pairs sharing one file ID and assert the file service receives one deduplicated batch read. Capture `AiChatRequest` and assert the user message has the TXT content block and image content block on the correct source message.

The assertion shape should be:

```java
ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
verify(aiChatService).stream(captor.capture());
assertThat(captor.getValue().messages()).hasSize(2);
assertThat(captor.getValue().messages().get(0).contents())
        .extracting(AiMessageContentDTO::type)
        .containsExactly(AiMessageContentType.TEXT, AiMessageContentType.IMAGE);
```

Run:

```bash
mvn -pl ai-platform-chat -am -Dtest=AiChatGatewayImplTest test
```

Expected: compilation failure because the gateway has no `FileContentService` dependency and `ChatMessagePair` has no `fileIds` yet in the gateway implementation.

- [ ] **Step 2: Implement one batched content read per AI request**

Inject `FileContentService` into `AiChatGatewayImpl`. Wrap the entire attachment read, DTO mapping, and `aiChatService.stream(request)` call in `Flux.defer`, so a file read or parse exception becomes a publisher error handled by `ChatStreamOrchestrator`:

```java
@Override
public Flux<AiStreamEvent> stream(ChatGenerationCommand command,
                                  ChatGenerationResult generation,
                                  List<ChatMessagePair> messages) {
    return Flux.defer(() -> {
        List<Long> fileIds = messages.stream()
                .flatMap(message -> message.fileIds().stream())
                .distinct()
                .toList();
        Map<Long, FileContent> contentById = fileContentService
                .readOwnedActive(command.userId(), fileIds)
                .stream()
                .collect(Collectors.toMap(FileContent::fileId, Function.identity()));
        List<AiMessageDTO> aiMessages = messages.stream()
                .map(message -> toAiMessage(message, contentById))
                .toList();
        AiGenerationOptions options = new AiGenerationOptions(
                command.modelId(), command.temperature(), command.topP(), command.maxOutputTokens());
        return aiChatService.stream(new AiChatRequest(
                command.requestId(), command.userId(), command.sessionId(),
                generation.userMessageId(), aiMessages, options));
    });
}
```

```java
private AiMessageDTO toAiMessage(ChatMessagePair message, Map<Long, FileContent> contentById) {
    List<AiMessageContentDTO> attachments = message.fileIds().stream()
            .map(fileId -> Optional.ofNullable(contentById.get(fileId))
                    .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "File content not found")))
            .map(this::toAiContent)
            .toList();
    return new AiMessageDTO(AiMessageRole.valueOf(message.role().name()), message.content(), attachments);
}

private AiMessageContentDTO toAiContent(FileContent content) {
    return content.text() != null
            ? AiMessageContentDTO.text(content.text())
            : AiMessageContentDTO.image(content.contentType(), content.bytes());
}
```

The `toAiContent` branch is safe because `FileContentService` only returns normalized TEXT or IMAGE values. Do not silently drop an attachment when the ID is missing from the returned map.

- [ ] **Step 3: Run the gateway and chat suites**

```bash
mvn -pl ai-platform-chat -am -Dtest=AiChatGatewayImplTest,ChatMessageFileServiceTest,ChatMessageServiceImplTest test
```

Expected: PASS.

- [ ] **Step 4: Commit AI request mapping**

```bash
git add ai-platform-chat/src/main/java ai-platform-chat/src/test/java
git commit -m "feat: include resolved attachments in AI requests"
```

---

### Task 5: Count attachment content in context and usage budgets

**Files:**
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/context/TokenBudgetManager.java:9-35`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/usage/TokenUsageCalculator.java:10-20`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/AiRequestValidator.java:9-20`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/context/TokenBudgetManagerTest.java`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/usage/TokenUsageCalculatorTest.java`
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/context/MessageConverterTest.java`

- [ ] **Step 1: Add failing budget tests**

Cover these exact cases:

```java
@Test
void countsPdfTextAndImageCostWhenTrimming() {
    AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "question",
            List.of(AiMessageContentDTO.text("extracted pdf text"),
                    AiMessageContentDTO.image("image/png", new byte[]{1})));

    assertThat(new TokenBudgetManager().trim(List.of(message), message.estimatedInputTokens()))
            .containsExactly(message);
}

@Test
void rejectsCurrentMessageWhenAttachmentCannotFit() {
    AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "question",
            List.of(AiMessageContentDTO.image("image/png", new byte[]{1})));

    assertThatThrownBy(() -> new TokenBudgetManager().trim(List.of(message), 1))
            .hasMessage("File content exceeds the available context");
}
```

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=TokenBudgetManagerTest,TokenUsageCalculatorTest test
```

Expected: failure because the current implementation counts only `message.content()` and silently drops an oversized latest message.

- [ ] **Step 2: Implement centralized message estimates**

Replace direct `content().length()` calculations in `TokenBudgetManager` and `TokenUsageCalculator` with `message.estimatedInputTokens()`. Keep the existing newest-first whole-message trimming behavior. If the newest message cannot fit and `message.hasImageContent()` or `message.contents()` is non-empty, throw `BusinessException(ErrorCode.AI_FILE_CONTENT_TOO_LARGE, "File content exceeds the available context")`.

Extend `AiRequestValidator` to reject malformed blocks:

- TEXT requires nonblank `text`.
- IMAGE requires a supported image MIME type and nonempty `data`.
- Non-USER messages cannot contain content blocks.

- [ ] **Step 3: Run all AI context tests and commit**

```bash
mvn -pl ai-platform-ai -am -Dtest=TokenBudgetManagerTest,TokenUsageCalculatorTest,MessageConverterTest test
```

Expected: PASS.

```bash
git add ai-platform-ai/src/main/java ai-platform-ai/src/test/java
git commit -m "feat: include attachments in AI context budgets"
```

---

### Task 6: Add model vision capability configuration and migration

**Files:**
- Create: `ai-platform-ai/src/main/resources/db/migration/V9__ai_model_vision.sql`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/entity/AiModel.java:11-34`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/request/AiModelUpsertRequest.java:13-26`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiModelDTO.java:3-4`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/dto/response/AiModelManagementDTO.java:5-21`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/AiModelConfigSnapshot.java:9-16`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/service/impl/AiModelServiceImpl.java:127-173`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/service/impl/AiModelServiceImplTest.java`
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/AiModelFactoryTest.java:17-32`
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java:43-57,99-113`

- [ ] **Step 1: Write the migration and failing service mapping test**

Create the Flyway migration:

```sql
alter table ai_model
    add column support_vision tinyint not null default 0 after support_stream;
```

Create a model-service test that passes `supportVision = 1` in `AiModelUpsertRequest`, captures the inserted `AiModel`, and asserts both `model.supportVision == 1` and the returned management DTO has `supportVision() == true`.

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiModelServiceImplTest test
```

Expected: compilation failure because the request, entity, DTOs, and service mapping do not contain the field.

- [ ] **Step 2: Implement the model field and mapping**

Use `Integer supportVision` in persistence/request objects to match `support_stream`. Use boolean values in response DTOs and the snapshot:

```java
public record AiModelConfigSnapshot(
        Long providerId,
        String providerName,
        AiProviderType providerType,
        String baseUrl,
        String apiKeyCiphertext,
        Integer connectTimeout,
        Integer readTimeout,
        Long modelId,
        String modelCode,
        Integer contextWindow,
        Integer maxOutputTokens,
        BigDecimal temperature,
        BigDecimal topP,
        Integer configVersion,
        boolean supportVision) {

    public static AiModelConfigSnapshot from(AiProvider provider, AiModel model) {
        return new AiModelConfigSnapshot(
                provider.id, provider.providerName, provider.providerType,
                provider.baseUrl, provider.apiKeyCiphertext, provider.connectTimeout,
                provider.readTimeout, model.id, model.modelCode, model.contextWindow,
                model.maxOutputTokens, model.temperature, model.topP, model.configVersion,
                Integer.valueOf(1).equals(model.supportVision));
    }
}
```

Default null request values to `0` in `AiModelServiceImpl.copy`. Include the flag in both available-model and management-model response constructors. Update every direct `AiModelConfigSnapshot` test constructor with the final boolean argument.

- [ ] **Step 3: Run model tests and commit**

```bash
mvn -pl ai-platform-ai -am -Dtest=AiModelServiceImplTest,AiModelFactoryTest,OllamaModelProviderTest test
```

Expected: PASS.

```bash
git add ai-platform-ai/src/main ai-platform-ai/src/test
 git commit -m "feat: configure model vision capability"
```

---

### Task 7: Reject images before unsupported provider calls

**Files:**
- Modify: `ai-platform-common/src/main/java/com/example/aichat/common/error/ErrorCode.java:5-16`
- Create: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/AiModelCapabilityValidator.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/core/DefaultAiChatService.java:22-72`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/core/AiModelCapabilityValidatorTest.java`

- [ ] **Step 1: Add error codes and failing capability tests**

Add these `BAD_REQUEST` codes to `ErrorCode`:

```java
AI_FILE_TYPE_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AI_FILE_TYPE_NOT_SUPPORTED", "File type is not supported for AI analysis"),
AI_FILE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "AI_FILE_PARSE_FAILED", "Unable to parse file for AI analysis"),
AI_FILE_CONTENT_EMPTY(HttpStatus.BAD_REQUEST, "AI_FILE_CONTENT_EMPTY", "File content is empty"),
AI_FILE_CONTENT_TOO_LARGE(HttpStatus.BAD_REQUEST, "AI_FILE_CONTENT_TOO_LARGE", "File content exceeds the available context"),
AI_VISION_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "AI_VISION_NOT_SUPPORTED", "The selected model does not support image input"),
```

Test:

```java
@Test
void acceptsTextAndPdfForNonVisionModel() {
    assertThatCode(() -> validator.validate(textRequest(), snapshot(false))).doesNotThrowAnyException();
}

@Test
void rejectsImageForNonVisionModel() {
    assertThatThrownBy(() -> validator.validate(imageRequest(), snapshot(false)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("The selected model does not support image input");
}

@Test
void acceptsImageForVisionModel() {
    assertThatCode(() -> validator.validate(imageRequest(), snapshot(true))).doesNotThrowAnyException();
}
```

Run:

```bash
mvn -pl ai-platform-ai -am -Dtest=AiModelCapabilityValidatorTest test
```

Expected: compilation failure because the validator and error codes do not exist.

- [ ] **Step 2: Implement capability validation**

Implement:

```java
@Component
public class AiModelCapabilityValidator {
    public void validate(AiChatRequest request, AiModelConfigSnapshot snapshot) {
        boolean hasImage = request.messages().stream().anyMatch(AiMessageDTO::hasImageContent);
        if (hasImage && !snapshot.supportVision()) {
            throw new BusinessException(ErrorCode.AI_VISION_NOT_SUPPORTED);
        }
    }
}
```

Inject it into `DefaultAiChatService` and call it after `router.route(request.options())` but before `usageRecorder.start(...)` and before `factory.get(snapshot).stream(...)`. This guarantees that an unsupported image never reaches a provider or creates an AI usage record.

- [ ] **Step 3: Run capability and full AI tests**

```bash
mvn -pl ai-platform-ai -am test
```

Expected: PASS.

- [ ] **Step 4: Commit capability validation**

```bash
git add ai-platform-common/src/main/java ai-platform-ai/src/main/java ai-platform-ai/src/test/java
git commit -m "feat: reject images for non-vision models"
```

---

### Task 8: Verify provider payloads and preserve text-only behavior

**Files:**
- Modify: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OllamaModelProviderTest.java:24-127`
- Create: `ai-platform-ai/src/test/java/com/example/aichat/ai/provider/adapter/OpenAiModelProviderTest.java`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OpenAiModelProvider.java:34-47`
- Modify: `ai-platform-ai/src/main/java/com/example/aichat/ai/provider/adapter/OllamaModelProvider.java:25-51`

- [ ] **Step 1: Add provider payload tests**

Extend the Ollama local HTTP server test with an image message and assert the request JSON contains an `images` array with the expected Base64 value. Add an OpenAI-compatible local HTTP server test that asserts the request contains a `content` array with an image item and its Base64 data URL.

Retain the current text-only assertions for both providers. DeepSeek and Qwen inherit `OpenAiModelProvider`, so the OpenAI-compatible test covers their shared converter path.

- [ ] **Step 2: Make only the minimal adapter changes required by LangChain4j**

Both adapters already pass the complete `List<ChatMessage>` returned by the shared converter into LangChain4j. Keep the production adapters unchanged unless the provider test exposes a concrete 1.21.0 serialization defect; do not add URL-based fallback logic or provider-specific file branches.

```java
MessageConverter.toLangChain4j(messages)
```

- [ ] **Step 3: Run provider tests and commit**

```bash
mvn -pl ai-platform-ai -am -Dtest=OllamaModelProviderTest,OpenAiModelProviderTest test
```

Expected: PASS, with no regression in the existing text-only response assertions.

```bash
git add ai-platform-ai/src/main/java ai-platform-ai/src/test/java
git commit -m "test: verify multimodal provider payloads"
```

---

### Task 9: Update API documentation and complete regression coverage

**Files:**
- Modify: `docs/backend-development-guide.md:160-178`
- Modify: `docs/frontend-api-integration.md` at the existing file upload/chat attachment sections

- [ ] **Step 1: Update backend documentation**

Replace the statement that attachments are not injected into AI context with a table covering:

| MIME type | AI handling | Model requirement |
| --- | --- | --- |
| `text/plain` | UTF-8 text injected into context | None |
| `application/pdf` | PDFBox text extraction injected into context | None |
| `image/jpeg`, `image/png`, `image/gif` | Base64 image content block | `supportVision = 1` |
| DOC/DOCX | Not supported for AI analysis | N/A |

Document that private MinIO URLs are never sent to providers, and that historical attachments are restored from `chat_message_file` during follow-up turns.

- [ ] **Step 2: Update frontend integration documentation**

Keep the existing JSON shape:

```json
{
  "content": "请总结这个文件",
  "fileIds": [1001]
}
```

Document that `fileIds` are required only on the message that attaches a new file; subsequent follow-ups can omit them. Document the `AI_VISION_NOT_SUPPORTED`, `AI_FILE_TYPE_NOT_SUPPORTED`, `AI_FILE_PARSE_FAILED`, `AI_FILE_CONTENT_EMPTY`, and `AI_FILE_CONTENT_TOO_LARGE` errors.

- [ ] **Step 3: Run the complete test suite**

```bash
mvn test
```

Expected: PASS across all modules.

- [ ] **Step 4: Inspect the final diff and verify behavior**

```bash
git status --short
git diff master...HEAD --stat
git diff master...HEAD --check
```

Confirm that no file contents, Base64 payloads, MinIO credentials, `.env` files, or `.DS_Store` are staged.

- [ ] **Step 5: Commit documentation and final regression changes**

```bash
git add docs/backend-development-guide.md docs/frontend-api-integration.md ai-platform-ai ai-platform-chat ai-platform-file ai-platform-common pom.xml
git commit -m "docs: document AI attachment analysis"
```

---

## Self-review checklist

- **Spec coverage:** Tasks 1–2 cover file parsing and provider-neutral content; Tasks 3–4 cover current, historical, and regenerated message attachments; Task 5 covers token accounting; Tasks 6–7 cover model capability and safe errors; Task 8 covers all shared provider paths; Task 9 covers docs and end-to-end regression.
- **Placeholder scan:** No step relies on a future decision, TODO, or unspecified file. Test fixtures are defined as in-memory PDFBox documents rather than an untracked binary.
- **Type consistency:** `FileContent`, `AiMessageContentDTO`, `ChatMessagePair.fileIds`, `supportVision`, and `AiModelConfigSnapshot.supportVision` are introduced before their consumers. The legacy two-argument `AiMessageDTO` constructor remains available to all existing tests.
- **Security check:** File reads remain behind ownership/status validation; images use Base64 bytes rather than private URLs; binary content is excluded from logs and audit records.
- **Scope check:** The plan is one connected feature with independently testable file, chat, and AI boundaries; it does not add DOCX parsing, OCR, or provider-specific upload APIs.
