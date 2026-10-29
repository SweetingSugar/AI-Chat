package com.example.aichat.file.service;

import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.file.content.FileContent;
import com.example.aichat.file.content.PdfTextExtractor;
import com.example.aichat.file.entity.FileMetadata;
import com.example.aichat.file.enums.FileStatus;
import com.example.aichat.file.mapper.FileMetadataMapper;
import com.example.aichat.file.service.impl.FileServiceImpl;
import com.example.aichat.file.storage.ObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileContentServiceTest {
    @Mock
    private FileMetadataMapper mapper;
    @Mock
    private ObjectStorage storage;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FileServiceImpl(mapper, storage, currentUserProvider,
                new com.example.aichat.file.config.FileStorageProperties(), new PdfTextExtractor());
    }

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
}
