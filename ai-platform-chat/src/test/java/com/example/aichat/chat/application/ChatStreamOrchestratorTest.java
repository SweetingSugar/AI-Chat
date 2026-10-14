package com.example.aichat.chat.application;

import com.example.aichat.ai.dto.response.AiChatResponse;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.dto.ChatMessageQueryDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.gateway.AiChatGateway;
import com.example.aichat.chat.manager.ChatGenerationLockManager;
import com.example.aichat.chat.manager.ChatGenerationTaskManager;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.ChatGenerationService;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.vo.ChatMessageVO;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import com.example.aichat.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamOrchestratorTest {
    private FakeGenerationService generationService;
    private FakeMessageService messageService;
    private FakeAiChatGateway aiChatGateway;
    private ChatStreamOrchestrator orchestrator;
    private ChatGenerationCommand command;
    private ChatGenerationResult generation;
    private AiModel model;

    @BeforeEach
    void setUp() {
        generationService = new FakeGenerationService();
        messageService = new FakeMessageService();
        aiChatGateway = new FakeAiChatGateway();
        orchestrator = new ChatStreamOrchestrator(new ChatGenerationLockManager(), new ChatGenerationTaskManager(),
                generationService, messageService, new ChatGenerationFinalizer(generationService), aiChatGateway);
        command = new ChatGenerationCommand(7L, 11L, "hello", 3L, "request-1", null, null, null, null, null);
        generation = new ChatGenerationResult(100L, 7L, 101L, 102L, "request-1", 3L, null);
        generationService.result = generation;
        model = new AiModel();
        model.id = 3L;
        model.modelCode = "test-model";
    }

    @Test
    void streamsDeltaAndPersistsSuccessfulCompletion() {
        AiChatResponse response = new AiChatResponse("request-1", 1L, 3L, "test-model",
                new AiTokenUsage(2, 3, 5, "estimate"));
        aiChatGateway.events = Flux.just(AiStreamEvent.delta("world"), AiStreamEvent.complete(response));

        List<ChatStreamEventVO> events = orchestrator.stream(command, model).collectList().block();

        assertEquals(3, events.size());
        assertEquals("world", events.get(1).delta());
        assertEquals(5, events.get(2).usage().totalTokens());
        assertEquals("world", generationService.completedContent);
    }

    @Test
    void providerFailureProducesFailedEventAndFinalizesFailure() {
        aiChatGateway.events = Flux.error(new IllegalStateException("provider down"));

        List<ChatStreamEventVO> events = orchestrator.stream(command, model).collectList().block();

        assertEquals(2, events.size());
        assertEquals(com.example.aichat.chat.enums.ChatStreamEventType.FAILED, events.get(1).type());
        assertEquals("AI_PROVIDER_ERROR", events.get(1).errorCode());
        assertEquals("AI_PROVIDER_ERROR", generationService.failureCode);
    }

    @Test
    void clientCancellationMarksGenerationInterrupted() {
        aiChatGateway.events = Flux.never();

        orchestrator.stream(command, model).subscribeWith(new BaseSubscriber<>() {
            @Override
            protected void hookOnSubscribe(Subscription subscription) {
                request(1);
            }

            @Override
            protected void hookOnNext(ChatStreamEventVO event) {
                if (event.type() == com.example.aichat.chat.enums.ChatStreamEventType.START) cancel();
            }
        });

        assertTrue(generationService.interrupted);
    }

    private static final class FakeGenerationService implements ChatGenerationService {
        private ChatGenerationResult result;
        private String completedContent;
        private String failureCode;
        private boolean interrupted;

        @Override public ChatGenerationResult start(ChatGenerationCommand command, AiModel model) { return result; }
        @Override public void complete(ChatGenerationResult generation, String content) { completedContent = content; }
        @Override public void fail(ChatGenerationResult generation, String content, String code, String message) { failureCode = code; }
        @Override public void interrupt(ChatGenerationResult generation, String content) { interrupted = true; }
    }

    private static final class FakeMessageService implements ChatMessageService {
        @Override public PageResult<ChatMessageVO> page(Long sessionId, ChatMessageQueryDTO query) { return null; }
        @Override public List<ChatMessageVO> listAll(Long sessionId) { return List.of(); }
        @Override public ChatMessage findOwned(Long messageId) { return null; }
        @Override public ChatMessage findPreviousUser(ChatMessage message) { return null; }
        @Override public ChatMessage saveUser(Long userId, Long sessionId, String content, String requestId) { return null; }
        @Override public ChatMessage saveAssistant(Long userId, Long sessionId, Long modelId, String requestId) { return null; }
        @Override public void bindTask(ChatGenerationResult generation) { }
        @Override public void markUserCompleted(ChatGenerationResult generation) { }
        @Override public List<ChatMessagePair> context(Long userId, Long sessionId, Long beforeSequence) {
            return List.of(new ChatMessagePair(ChatMessageRole.USER, "hello"));
        }
        @Override public void append(ChatGenerationResult generation, String content) { }
        @Override public void complete(ChatGenerationResult generation, String content) { }
        @Override public void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage) { }
        @Override public void interrupt(ChatGenerationResult generation, String content) { }
    }

    private static final class FakeAiChatGateway implements AiChatGateway {
        private Flux<AiStreamEvent> events = Flux.empty();

        @Override
        public Flux<AiStreamEvent> stream(ChatGenerationCommand command, ChatGenerationResult generation,
                                          List<ChatMessagePair> messages) {
            return events;
        }
    }
}
