package com.example.aichat.chat.gateway.impl;

import com.example.aichat.ai.api.AiChatService;
import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageContentType;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.file.content.FileContent;
import com.example.aichat.file.service.FileContentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatGatewayImplTest {
    @Mock
    private AiChatService aiChatService;
    @Mock
    private FileContentService fileContentService;

    private AiChatGatewayImpl gateway;

    @BeforeEach
    void setUp() {
        gateway = new AiChatGatewayImpl(aiChatService, fileContentService);
    }

    @Test
    void mapsResolvedAttachmentsIntoTheirSourceMessages() {
        when(fileContentService.readOwnedActive(100L, List.of(10L, 11L)))
                .thenReturn(List.of(
                        new FileContent(10L, "notes.txt", "text/plain", "extracted text", null),
                        new FileContent(11L, "image.png", "image/png", null, new byte[]{1, 2})));
        when(aiChatService.stream(any())).thenReturn(Flux.just(AiStreamEvent.delta("ok")));

        gateway.stream(command(), generation(), List.of(
                        new ChatMessagePair(com.example.aichat.chat.enums.ChatMessageRole.USER,
                                "question", List.of(10L, 11L)),
                        new ChatMessagePair(com.example.aichat.chat.enums.ChatMessageRole.ASSISTANT,
                                "answer", List.of())))
                .blockLast();

        ArgumentCaptor<AiChatRequest> captor = ArgumentCaptor.forClass(AiChatRequest.class);
        verify(aiChatService).stream(captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
        assertThat(captor.getValue().messages().get(0).role()).isEqualTo(AiMessageRole.USER);
        assertThat(captor.getValue().messages().get(0).contents())
                .extracting(AiMessageContentDTO::type)
                .containsExactly(AiMessageContentType.TEXT, AiMessageContentType.IMAGE);
        assertThat(captor.getValue().messages().get(1).contents()).isEmpty();
        verify(fileContentService).readOwnedActive(100L, List.of(10L, 11L));
    }

    @Test
    void signalsFileParseErrorsThroughTheReturnedFlux() {
        RuntimeException failure = new RuntimeException("parse failure");
        when(fileContentService.readOwnedActive(100L, List.of(10L))).thenThrow(failure);

        Flux<AiStreamEvent> stream = gateway.stream(command(), generation(), List.of(
                new ChatMessagePair(com.example.aichat.chat.enums.ChatMessageRole.USER,
                        "question", List.of(10L))));

        assertThatThrownBy(stream::blockLast)
                .hasMessage("parse failure");
    }

    private ChatGenerationCommand command() {
        return new ChatGenerationCommand(100L, 200L, "question", List.of(), 300L,
                "request", null, null, null, null, null);
    }

    private ChatGenerationResult generation() {
        return new ChatGenerationResult(1L, 100L, 2L, 3L, "request", 300L, null);
    }
}
