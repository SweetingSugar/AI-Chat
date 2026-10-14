package com.example.aichat.chat.service.impl;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.application.ChatRegenerateOrchestrator;
import com.example.aichat.chat.application.ChatStreamOrchestrator;
import com.example.aichat.chat.dto.ChatRegenerateDTO;
import com.example.aichat.chat.dto.ChatRequestDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.exception.ChatErrorCode;
import com.example.aichat.chat.gateway.AiModelGateway;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.service.ChatService;
import com.example.aichat.chat.service.ChatSessionService;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {
    private final ChatSessionService sessionService;
    private final ChatMessageService messageService;
    private final ChatUserGateway userGateway;
    private final AiModelGateway modelGateway;
    private final ChatStreamOrchestrator streamOrchestrator;
    private final ChatRegenerateOrchestrator regenerateOrchestrator;

    public ChatServiceImpl(ChatSessionService sessionService, ChatMessageService messageService,
                           ChatUserGateway userGateway, AiModelGateway modelGateway,
                           ChatStreamOrchestrator streamOrchestrator,
                           ChatRegenerateOrchestrator regenerateOrchestrator) {
        this.sessionService = sessionService;
        this.messageService = messageService;
        this.userGateway = userGateway;
        this.modelGateway = modelGateway;
        this.streamOrchestrator = streamOrchestrator;
        this.regenerateOrchestrator = regenerateOrchestrator;
    }

    @Override
    public Flux<ChatStreamEventVO> stream(Long sessionId, ChatRequestDTO request) {
        CurrentUser user = userGateway.requireCurrentUser();
        var session = sessionService.requireOwned(sessionId);
        AiModel model = modelGateway.resolve(request.modelId(), session.defaultModelId);
        ChatGenerationCommand command = new ChatGenerationCommand(user.id(), sessionId, request.content(), model.id,
                requestId(request.requestId()), request.temperature(), request.topP(), request.maxOutputTokens(), null, null);
        return streamOrchestrator.stream(command, model);
    }

    @Override
    public Flux<ChatStreamEventVO> regenerate(Long sessionId, ChatRegenerateDTO request) {
        CurrentUser user = userGateway.requireCurrentUser();
        var session = sessionService.requireOwned(sessionId);
        ChatMessage source = messageService.findOwned(request.messageId());
        if (!Long.valueOf(sessionId).equals(source.sessionId) || source.role != ChatMessageRole.ASSISTANT) {
            throw new BusinessException(ChatErrorCode.INVALID_REGENERATE_MESSAGE.errorCode(), "Only an assistant message in this session can be regenerated");
        }
        ChatMessage userMessage = messageService.findPreviousUser(source);
        AiModel model = modelGateway.resolve(request.modelId(), session.defaultModelId);
        ChatGenerationCommand command = new ChatGenerationCommand(user.id(), sessionId, userMessage.content, model.id,
                requestId(request.requestId()), request.temperature(), request.topP(), request.maxOutputTokens(),
                userMessage.id, source.id);
        return regenerateOrchestrator.stream(command, model);
    }

    private String requestId(String requestId) {
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
