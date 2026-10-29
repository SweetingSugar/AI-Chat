package com.example.aichat.chat.service;

import com.example.aichat.chat.entity.ChatMessageFile;
import com.example.aichat.chat.mapper.ChatMessageFileMapper;
import com.example.aichat.chat.service.impl.ChatMessageFileServiceImpl;
import com.example.aichat.file.service.FileAccessService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageFileServiceTest {

    @Mock
    private ChatMessageFileMapper mapper;
    @Mock
    private FileAccessService fileAccessService;

    private ChatMessageFileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageFileServiceImpl(mapper, fileAccessService);
    }

    @Test
    void bindsOnlyFilesOwnedByCurrentUser() {
        when(fileAccessService.validateOwnedActiveFiles(100L, List.of(10L, 11L)))
                .thenReturn(List.of(file(10L), file(11L)));

        service.bind(200L, 100L, List.of(10L, 11L));

        ArgumentCaptor<ChatMessageFile> captor = ArgumentCaptor.forClass(ChatMessageFile.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(value -> value.fileId)
                .containsExactly(10L, 11L);
        assertThat(captor.getAllValues()).extracting(value -> value.sortOrder)
                .containsExactly(0, 1);
    }

    @Test
    void rejectsMissingOrInactiveFile() {
        when(fileAccessService.validateOwnedActiveFiles(100L, List.of(10L)))
                .thenThrow(new RuntimeException("file not available"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.bind(200L, 100L, List.of(10L)))
                .hasMessage("file not available");
        verify(mapper, never()).insert(any(ChatMessageFile.class));
    }

    @Test
    void returnsFileIdsGroupedInRelationOrder() {
        ChatMessageFile first = new ChatMessageFile();
        first.messageId = 20L;
        first.fileId = 11L;
        first.sortOrder = 0;
        ChatMessageFile second = new ChatMessageFile();
        second.messageId = 20L;
        second.fileId = 10L;
        second.sortOrder = 1;
        when(mapper.selectByMessageIds(List.of(20L))).thenReturn(List.of(first, second));

        assertThat(service.findFileIdsByMessageIds(List.of(20L)))
                .containsEntry(20L, List.of(11L, 10L));
    }

    @Test
    void preservesRequestedFileOrder() {
        when(fileAccessService.validateOwnedActiveFiles(100L, List.of(11L, 10L)))
                .thenReturn(List.of(file(11L), file(10L)));

        service.bind(200L, 100L, List.of(11L, 10L));

        ArgumentCaptor<ChatMessageFile> captor = ArgumentCaptor.forClass(ChatMessageFile.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(value -> value.fileId)
                .containsExactly(11L, 10L);
    }

    private FileMetadataVO file(Long id) {
        return new FileMetadataVO(id, 100L, id + ".txt", "text/plain", 1L, null, null, null);
    }
}
