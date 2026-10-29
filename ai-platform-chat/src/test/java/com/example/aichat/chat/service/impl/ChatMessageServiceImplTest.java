package com.example.aichat.chat.service.impl;

import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;
import com.example.aichat.chat.mapper.ChatMessageFileMapper;
import com.example.aichat.chat.mapper.ChatMessageMapper;
import com.example.aichat.chat.mapper.ChatSessionMapper;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.ChatMessageFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceImplTest {
    @Mock
    private ChatMessageMapper messageMapper;
    @Mock
    private ChatSessionMapper sessionMapper;
    @Mock
    private ChatUserGateway userGateway;
    @Mock
    private ChatMessageFileService messageFileService;

    private ChatMessageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageServiceImpl(messageMapper, sessionMapper, userGateway, messageFileService);
    }

    @Test
    void includesHistoricalAttachmentIdsInContextPairs() {
        ChatMessage user = message(1L, ChatMessageRole.USER, "summarize", ChatMessageStatus.COMPLETED);
        ChatMessage assistant = message(2L, ChatMessageRole.ASSISTANT, "summary", ChatMessageStatus.COMPLETED);
        when(messageMapper.selectHistory(100L, 200L, null)).thenReturn(List.of(user, assistant));
        when(messageFileService.findFileIdsByMessageIds(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, List.of(10L, 11L), 2L, List.of()));

        List<ChatMessagePair> context = service.context(100L, 200L, null);

        assertThat(context).hasSize(2);
        assertThat(context.get(0).fileIds()).containsExactly(10L, 11L);
        assertThat(context.get(1).fileIds()).isEmpty();
    }

    @Test
    void excludesPendingAssistantMessagesFromContext() {
        ChatMessage user = message(1L, ChatMessageRole.USER, "question", ChatMessageStatus.COMPLETED);
        ChatMessage pending = message(2L, ChatMessageRole.ASSISTANT, "", ChatMessageStatus.STREAMING);
        when(messageMapper.selectHistory(100L, 200L, null)).thenReturn(List.of(user, pending));
        when(messageFileService.findFileIdsByMessageIds(List.of(1L)))
                .thenReturn(Map.of(1L, List.of(10L)));

        assertThat(service.context(100L, 200L, null)).singleElement()
                .satisfies(pair -> assertThat(pair.fileIds()).containsExactly(10L));
    }

    private ChatMessage message(Long id, ChatMessageRole role, String content, ChatMessageStatus status) {
        ChatMessage message = new ChatMessage();
        message.id = id;
        message.role = role;
        message.content = content;
        message.status = status;
        return message;
    }
}
