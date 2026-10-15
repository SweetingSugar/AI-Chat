package com.example.aichat.chat.service;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.dto.ChatMessageQueryDTO;
import com.example.aichat.chat.dto.CreateSessionDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.gateway.AiModelGateway;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.chat.mapper.ChatSessionMapper;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.impl.ChatSessionServiceImpl;
import com.example.aichat.chat.vo.ChatMessageVO;
import com.example.aichat.chat.vo.ChatSessionVO;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSessionServiceImplTest {
    @Test
    void createUsesSystemDefaultModelWhenRequestDoesNotProvideOne() {
        ChatSession[] insertedSession = new ChatSession[1];
        ChatSessionMapper sessionMapper = (ChatSessionMapper) Proxy.newProxyInstance(
                ChatSessionMapper.class.getClassLoader(), new Class<?>[]{ChatSessionMapper.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("insert")) {
                        insertedSession[0] = (ChatSession) args[0];
                        return 1;
                    }
                    return null;
                });
        FakeUserGateway userGateway = new FakeUserGateway();
        FakeModelGateway modelGateway = new FakeModelGateway();
        AiModel defaultModel = new AiModel();
        defaultModel.id = 42L;
        modelGateway.model = defaultModel;
        ChatSessionServiceImpl service = new ChatSessionServiceImpl(sessionMapper, userGateway,
                modelGateway, new NoopMessageService());

        ChatSessionVO result = service.create(new CreateSessionDTO("title", null));

        assertNull(modelGateway.requestedModelId);
        assertNull(modelGateway.sessionDefaultModelId);
        assertTrue(modelGateway.resolveCalled);
        assertEquals(42L, insertedSession[0].defaultModelId);
        assertEquals(42L, result.defaultModelId());
    }

    private static final class FakeUserGateway implements ChatUserGateway {
        @Override
        public CurrentUser requireCurrentUser() {
            return new CurrentUser(7L, "alice");
        }
    }

    private static final class FakeModelGateway implements AiModelGateway {
        private Long requestedModelId;
        private Long sessionDefaultModelId;
        private AiModel model;
        private boolean resolveCalled;

        @Override
        public AiModel resolve(Long requestedModelId, Long sessionDefaultModelId) {
            this.resolveCalled = true;
            this.requestedModelId = requestedModelId;
            this.sessionDefaultModelId = sessionDefaultModelId;
            return model;
        }
    }

    private static final class NoopMessageService implements ChatMessageService {
        @Override public PageResult<ChatMessageVO> page(Long sessionId, ChatMessageQueryDTO query) { return null; }
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
