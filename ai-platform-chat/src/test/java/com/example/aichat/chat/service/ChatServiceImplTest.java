package com.example.aichat.chat.service;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.application.ChatRegenerateOrchestrator;
import com.example.aichat.chat.application.ChatStreamOrchestrator;
import com.example.aichat.chat.dto.ChatRequestDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.enums.ChatSessionStatus;
import com.example.aichat.chat.gateway.AiModelGateway;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.impl.ChatServiceImpl;
import com.example.aichat.chat.vo.ChatMessageVO;
import com.example.aichat.chat.vo.ChatSessionDetailVO;
import com.example.aichat.chat.vo.ChatSessionVO;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.security.CurrentUser;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatServiceImplTest {
    @Test
    void singleRequestModelOverridesSessionDefaultModel() {
        RecordingStreamOrchestrator streamOrchestrator = new RecordingStreamOrchestrator();
        FakeModelGateway modelGateway = new FakeModelGateway();
        modelGateway.model = model(9L);
        ChatServiceImpl chatService = service(session(11L, 2L), modelGateway, streamOrchestrator);

        chatService.stream(11L, new ChatRequestDTO("hello", 9L, "request-1", null, null, null)).blockLast();

        assertEquals(9L, modelGateway.requestedModelId);
        assertEquals(2L, modelGateway.sessionDefaultModelId);
        assertEquals(9L, streamOrchestrator.command.modelId());
        assertEquals(7L, streamOrchestrator.command.userId());
    }

    @Test
    void missingSessionDefaultFallsBackToSystemDefaultModel() {
        RecordingStreamOrchestrator streamOrchestrator = new RecordingStreamOrchestrator();
        FakeModelGateway modelGateway = new FakeModelGateway();
        modelGateway.model = model(3L);
        ChatServiceImpl chatService = service(session(11L, null), modelGateway, streamOrchestrator);

        chatService.stream(11L, new ChatRequestDTO("hello", null, null, null, null, null)).blockLast();

        assertEquals(null, modelGateway.requestedModelId);
        assertEquals(null, modelGateway.sessionDefaultModelId);
        assertEquals(3L, streamOrchestrator.command.modelId());
    }

    private ChatServiceImpl service(ChatSession session, FakeModelGateway modelGateway,
                                    RecordingStreamOrchestrator streamOrchestrator) {
        return new ChatServiceImpl(new FakeSessionService(session), new NoopMessageService(),
                () -> new CurrentUser(7L, "alice"), modelGateway, streamOrchestrator,
                new ChatRegenerateOrchestrator(streamOrchestrator));
    }

    private ChatSession session(Long id, Long defaultModelId) {
        ChatSession session = new ChatSession();
        session.id = id;
        session.userId = 7L;
        session.defaultModelId = defaultModelId;
        session.status = ChatSessionStatus.ACTIVE;
        return session;
    }

    private static AiModel model(Long id) {
        AiModel model = new AiModel();
        model.id = id;
        model.modelCode = "test-model";
        return model;
    }

    private static final class FakeModelGateway implements AiModelGateway {
        private Long requestedModelId;
        private Long sessionDefaultModelId;
        private AiModel model;

        @Override
        public AiModel resolve(Long requestedModelId, Long sessionDefaultModelId) {
            this.requestedModelId = requestedModelId;
            this.sessionDefaultModelId = sessionDefaultModelId;
            return model;
        }
    }

    private static final class FakeSessionService implements ChatSessionService {
        private final ChatSession session;

        private FakeSessionService(ChatSession session) {
            this.session = session;
        }

        @Override public ChatSessionVO create(com.example.aichat.chat.dto.CreateSessionDTO request) { return null; }
        @Override public PageResult<ChatSessionVO> page(int page, int size) { return null; }
        @Override public ChatSessionDetailVO detail(Long sessionId) { return null; }
        @Override public ChatSessionVO update(Long sessionId, com.example.aichat.chat.dto.UpdateSessionDTO request) { return null; }
        @Override public ChatSessionVO pin(Long sessionId, boolean pinned) { return null; }
        @Override public void delete(Long sessionId) { }
        @Override public ChatSession requireOwned(Long sessionId) { return session; }
    }

    private static final class RecordingStreamOrchestrator extends ChatStreamOrchestrator {
        private ChatGenerationCommand command;

        private RecordingStreamOrchestrator() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Flux<ChatStreamEventVO> stream(ChatGenerationCommand command, AiModel model) {
            this.command = command;
            return Flux.empty();
        }
    }

    private static final class NoopMessageService implements ChatMessageService {
        @Override public PageResult<ChatMessageVO> page(Long sessionId, com.example.aichat.chat.dto.ChatMessageQueryDTO query) { return null; }
        @Override public List<ChatMessageVO> listAll(Long sessionId) { return List.of(); }
        @Override public ChatMessage findOwned(Long messageId) { return null; }
        @Override public ChatMessage findPreviousUser(ChatMessage message) { return null; }
        @Override public ChatMessage saveUser(Long userId, Long sessionId, String content, String requestId) { return null; }
        @Override public ChatMessage saveAssistant(Long userId, Long sessionId, Long modelId, String requestId) { return null; }
        @Override public void bindTask(ChatGenerationResult generation) { }
        @Override public void markUserCompleted(ChatGenerationResult generation) { }
        @Override public List<ChatMessagePair> context(Long userId, Long sessionId, Long beforeSequence) { return List.of(); }
        @Override public void append(ChatGenerationResult generation, String content) { }
        @Override public void complete(ChatGenerationResult generation, String content) { }
        @Override public void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage) { }
        @Override public void interrupt(ChatGenerationResult generation, String content) { }
    }
}
