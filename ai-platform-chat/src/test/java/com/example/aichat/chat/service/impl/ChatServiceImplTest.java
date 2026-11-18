package com.example.aichat.chat.service.impl;

import com.example.aichat.agent.model.AgentRuntimeConfig;
import com.example.aichat.agent.service.AgentRuntimeService;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.application.ChatRegenerateOrchestrator;
import com.example.aichat.chat.application.ChatStreamOrchestrator;
import com.example.aichat.chat.dto.ChatRequestDTO;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.service.ChatKnowledgeService;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.service.ChatSessionService;
import com.example.aichat.chat.gateway.AiModelGateway;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.file.service.FileAccessService;
import com.example.aichat.common.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {
    @Mock
    private ChatSessionService sessionService;
    @Mock
    private ChatMessageService messageService;
    @Mock
    private ChatUserGateway userGateway;
    @Mock
    private AiModelGateway modelGateway;
    @Mock
    private FileAccessService fileAccessService;
    @Mock
    private ChatKnowledgeService knowledgeService;
    @Mock
    private ChatStreamOrchestrator streamOrchestrator;
    @Mock
    private ChatRegenerateOrchestrator regenerateOrchestrator;
    @Mock
    private AgentRuntimeService agentRuntimeService;

    private ChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChatServiceImpl(sessionService, messageService, userGateway, modelGateway,
                fileAccessService, knowledgeService, streamOrchestrator, regenerateOrchestrator,
                agentRuntimeService);
    }

    @Test
    void agentChatUsesRuntimeModelAndResourcesInsteadOfRequestOverrides() {
        when(userGateway.requireCurrentUser()).thenReturn(new CurrentUser(7L, "user"));
        ChatSession session = new ChatSession();
        session.id = 5L;
        session.userId = 7L;
        session.defaultModelId = 99L;
        when(sessionService.requireOwned(5L)).thenReturn(session);
        when(fileAccessService.validateOwnedActiveFiles(7L, List.of())).thenReturn(List.of());
        when(agentRuntimeService.resolve(7L, 100L)).thenReturn(new AgentRuntimeConfig(
                100L, 2L, "You are support.", List.of(11L), List.of("order_query")));
        AiModel model = chatModel(2L);
        when(modelGateway.resolve(2L, 99L)).thenReturn(model);
        when(streamOrchestrator.stream(any(), eq(model))).thenReturn(Flux.empty());

        service.stream(5L, new ChatRequestDTO("check order", List.of(), List.of(999L), 88L,
                "request-1", 0.7, 0.9, 1024, List.of("unbound_tool"), 100L));

        ArgumentCaptor<ChatGenerationCommand> captor = ArgumentCaptor.forClass(ChatGenerationCommand.class);
        verify(streamOrchestrator).stream(captor.capture(), eq(model));
        assertThat(captor.getValue().modelId()).isEqualTo(2L);
        assertThat(captor.getValue().knowledgeBaseIds()).containsExactly(11L);
        assertThat(captor.getValue().toolCodes()).containsExactly("order_query");
        assertThat(captor.getValue().systemPrompt()).isEqualTo("You are support.");
    }

    @Test
    void legacyChatDoesNotResolveAnAgent() {
        when(userGateway.requireCurrentUser()).thenReturn(new CurrentUser(7L, "user"));
        ChatSession session = new ChatSession();
        session.defaultModelId = 99L;
        when(sessionService.requireOwned(5L)).thenReturn(session);
        when(fileAccessService.validateOwnedActiveFiles(7L, List.of())).thenReturn(List.of());
        AiModel model = chatModel(88L);
        when(modelGateway.resolve(88L, 99L)).thenReturn(model);
        when(streamOrchestrator.stream(any(), eq(model))).thenReturn(Flux.empty());

        service.stream(5L, new ChatRequestDTO("hello", List.of(), List.of(11L), 88L,
                "request-1", null, null, null, List.of("order_query"), null));

        verify(modelGateway).resolve(88L, 99L);
        verify(streamOrchestrator).stream(any(), eq(model));
    }

    private AiModel chatModel(Long id) {
        AiModel model = new AiModel();
        model.id = id;
        model.modelType = "CHAT";
        model.status = 1;
        return model;
    }
}
