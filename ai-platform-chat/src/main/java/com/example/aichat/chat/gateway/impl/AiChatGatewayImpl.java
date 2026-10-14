package com.example.aichat.chat.gateway.impl;

import com.example.aichat.ai.api.AiChatService;
import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.gateway.AiChatGateway;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class AiChatGatewayImpl implements AiChatGateway {
    private final AiChatService aiChatService;

    public AiChatGatewayImpl(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @Override
    public Flux<AiStreamEvent> stream(ChatGenerationCommand command, ChatGenerationResult generation,
                                      List<ChatMessagePair> messages) {
        List<AiMessageDTO> aiMessages = messages.stream()
                .map(message -> new AiMessageDTO(AiMessageRole.valueOf(message.role().name()), message.content()))
                .toList();
        AiGenerationOptions options = new AiGenerationOptions(command.modelId(), command.temperature(),
                command.topP(), command.maxOutputTokens());
        AiChatRequest request = new AiChatRequest(command.requestId(), command.userId(), command.sessionId(),
                generation.userMessageId(), aiMessages, options);
        return aiChatService.stream(request);
    }
}
