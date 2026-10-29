package com.example.aichat.chat.gateway.impl;

import com.example.aichat.ai.api.AiChatService;
import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.gateway.AiChatGateway;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.file.content.FileContent;
import com.example.aichat.file.service.FileContentService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AiChatGatewayImpl implements AiChatGateway {
    private final AiChatService aiChatService;
    private final FileContentService fileContentService;

    public AiChatGatewayImpl(AiChatService aiChatService, FileContentService fileContentService) {
        this.aiChatService = aiChatService;
        this.fileContentService = fileContentService;
    }

    @Override
    public Flux<AiStreamEvent> stream(ChatGenerationCommand command, ChatGenerationResult generation,
                                      List<ChatMessagePair> messages) {
        return Flux.defer(() -> {
            List<Long> fileIds = messages.stream()
                    .flatMap(message -> message.fileIds().stream())
                    .distinct()
                    .toList();
            Map<Long, FileContent> contentById = fileContentService
                    .readOwnedActive(command.userId(), fileIds)
                    .stream()
                    .collect(Collectors.toMap(FileContent::fileId, Function.identity()));
            List<AiMessageDTO> aiMessages = messages.stream()
                    .map(message -> toAiMessage(message, contentById))
                    .toList();
            AiGenerationOptions options = new AiGenerationOptions(command.modelId(), command.temperature(),
                    command.topP(), command.maxOutputTokens());
            AiChatRequest request = new AiChatRequest(command.requestId(), command.userId(), command.sessionId(),
                    generation.userMessageId(), aiMessages, options);
            return aiChatService.stream(request);
        });
    }

    private AiMessageDTO toAiMessage(ChatMessagePair message, Map<Long, FileContent> contentById) {
        List<AiMessageContentDTO> attachments = message.fileIds().stream()
                .map(fileId -> Optional.ofNullable(contentById.get(fileId))
                        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                                "File content not found")))
                .map(this::toAiContent)
                .toList();
        return new AiMessageDTO(AiMessageRole.valueOf(message.role().name()), message.content(), attachments);
    }

    private AiMessageContentDTO toAiContent(FileContent content) {
        return content.text() != null
                ? AiMessageContentDTO.text(content.text())
                : AiMessageContentDTO.image(content.contentType(), content.bytes());
    }
}
