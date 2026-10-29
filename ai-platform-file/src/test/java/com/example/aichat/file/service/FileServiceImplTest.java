package com.example.aichat.file.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.file.config.FileStorageProperties;
import com.example.aichat.file.content.PdfTextExtractor;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.entity.FileMetadata;
import com.example.aichat.file.enums.FileStatus;
import com.example.aichat.file.mapper.FileMetadataMapper;
import com.example.aichat.file.service.impl.FileServiceImpl;
import com.example.aichat.file.storage.ObjectStorage;
import com.example.aichat.file.vo.FileDownloadUrlVO;
import com.example.aichat.file.vo.FileMetadataVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
        service = new FileServiceImpl(mapper, storage, currentUserProvider, properties, new PdfTextExtractor());
    }

    @Test
    void queriesCompleteCalendarDateRange() {
        when(mapper.selectPage(any(), any())).thenReturn(new Page<>());

        FileQueryRequest request = new FileQueryRequest();
        request.setCreatedFrom(LocalDate.of(2026, 10, 1));
        request.setCreatedTo(LocalDate.of(2026, 10, 27));

        service.page(request);

        ArgumentCaptor<QueryWrapper<FileMetadata>> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(mapper).selectPage(any(), queryCaptor.capture());
        queryCaptor.getValue().getSqlSegment();
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(2026, 10, 1, 0, 0),
                        LocalDateTime.of(2026, 10, 28, 0, 0));
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
    void createsAdminPresignedUrlForActiveFileRegardlessOfOwner() {
        FileMetadata metadata = metadata(200L, FileStatus.ACTIVE);
        metadata.bucketName = "files";
        metadata.objectKey = "users/200/a.txt";
        when(mapper.selectById(1001L)).thenReturn(metadata);
        when(storage.createPresignedGetUrl(any(), any(), any())).thenReturn("https://download.example/file");

        FileDownloadUrlVO result = service.createAdminDownloadUrl(1001L);

        assertThat(result.fileId()).isEqualTo(1001L);
        assertThat(result.url()).isEqualTo("https://download.example/file");
        verify(storage).createPresignedGetUrl(any(), any(), any());
        verifyNoInteractions(currentUserProvider);
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

    @Test
    void marksMetadataAsDeletedAfterStorageRemoval() {
        FileMetadata metadata = metadata(100L, FileStatus.ACTIVE);
        metadata.bucketName = "files";
        metadata.objectKey = "users/100/a.txt";
        when(mapper.selectById(1001L)).thenReturn(metadata);

        service.deleteOwned(1001L);

        verify(mapper, times(2)).updateById(metadata);
        verify(mapper).deleteById(1001L);
    }

    @Test
    void uploadsBatchInInputOrder() {
        MockMultipartFile first = new MockMultipartFile("files", "first.txt", "text/plain", "one".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "second.txt", "text/plain", "two".getBytes());

        List<FileMetadataVO> result = service.uploadBatch(List.of(first, second));

        assertThat(result).extracting(FileMetadataVO::originalName)
                .containsExactly("first.txt", "second.txt");
        verify(storage, times(2)).put(eq("files"), any(), any(), anyLong(), eq("text/plain"));
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

    @Test
    void cleansPreviouslyUploadedObjectsAndMetadataWhenLaterFileFails() {
        MockMultipartFile first = new MockMultipartFile("files", "first.txt", "text/plain", "one".getBytes());
        MockMultipartFile second = new MockMultipartFile("files", "second.txt", "text/plain", "two".getBytes());
        AtomicInteger insertCount = new AtomicInteger();
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
