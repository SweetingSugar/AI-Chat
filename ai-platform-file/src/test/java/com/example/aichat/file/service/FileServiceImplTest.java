package com.example.aichat.file.service;

import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.file.config.FileStorageProperties;
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
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private FileMetadataMapper mapper;
    @Mock
    private ObjectStorage storage;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private FileStorageProperties properties;
    private FileServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new FileStorageProperties();
        properties.setBucket("files");
        properties.setMaxFileSize(10);
        properties.setAllowedContentTypes(java.util.Set.of("text/plain"));
        lenient().when(currentUserProvider.findCurrentUser())
                .thenReturn(Optional.of(new CurrentUser(100L, "alice")));
        lenient().doCallRealMethod().when(currentUserProvider).requireCurrentUser();
        service = new FileServiceImpl(mapper, storage, currentUserProvider, properties);
    }

    @Test
    void rejectsDisallowedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[1]);

        assertThatThrownBy(() -> service.upload(file))
                .hasMessage("File type is not allowed");
    }

    @Test
    void rejectsFileLargerThanConfiguredLimit() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", new byte[11]);

        assertThatThrownBy(() -> service.upload(file))
                .hasMessage("File exceeds the maximum allowed size");
    }

    @Test
    void rejectsAccessToAnotherUsersFile() {
        FileMetadata metadata = metadata(200L, FileStatus.ACTIVE);
        when(mapper.selectById(1001L)).thenReturn(metadata);

        assertThatThrownBy(() -> service.findAccessible(1001L))
                .hasMessage("File access denied");
    }

    @Test
    void removesObjectWhenMetadataInsertFails() {
        MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes());
        doThrow(new IllegalStateException("database failure")).when(mapper).insert(any(FileMetadata.class));

        assertThatThrownBy(() -> service.upload(file))
                .isInstanceOf(IllegalStateException.class);
        verify(storage).delete(any(), any());
    }

    @Test
    void createsPresignedUrlOnlyForActiveOwnedFile() {
        FileMetadata metadata = metadata(100L, FileStatus.ACTIVE);
        metadata.bucketName = "files";
        metadata.objectKey = "users/100/a.txt";
        when(mapper.selectById(1001L)).thenReturn(metadata);
        when(storage.createPresignedGetUrl(any(), any(), any())).thenReturn("https://download.example/file");

        service.createDownloadUrl(1001L);

        verify(storage).createPresignedGetUrl(any(), any(), any());
    }

    @Test
    void rejectsPdfWithInvalidSignature() {
        properties.setAllowedContentTypes(java.util.Set.of("text/plain", "application/pdf"));
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", "not a pdf".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .hasMessage("File type is not allowed");
    }

    @Test
    void rejectsFileNameLongerThanDatabaseLimit() {
        String fileName = "a".repeat(256) + ".txt";
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.upload(file))
                .hasMessage("File name is too long");
    }

    @Test
    void rejectsAttachmentOwnedByAnotherUser() {
        when(mapper.selectBatchIds(java.util.List.of(1001L)))
                .thenReturn(java.util.List.of(metadata(200L, FileStatus.ACTIVE)));

        assertThatThrownBy(() -> service.validateOwnedActiveFiles(100L, java.util.List.of(1001L)))
                .hasMessage("File access denied");
    }

    private FileMetadata metadata(Long userId, FileStatus status) {
        FileMetadata metadata = new FileMetadata();
        metadata.id = 1001L;
        metadata.userId = userId;
        metadata.status = status;
        metadata.originalName = "a.txt";
        metadata.contentType = "text/plain";
        metadata.fileSize = 5L;
        return metadata;
    }
}
