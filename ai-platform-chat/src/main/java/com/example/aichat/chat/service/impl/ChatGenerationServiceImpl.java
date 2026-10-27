package com.example.aichat.chat.service.impl;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.entity.ChatGenerationTask;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatGenerationStatus;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.exception.ChatErrorCode;
import com.example.aichat.chat.mapper.ChatGenerationTaskMapper;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.service.ChatGenerationService;
import com.example.aichat.chat.service.ChatMessageFileService;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.common.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ChatGenerationServiceImpl implements ChatGenerationService {
    private final ChatGenerationTaskMapper taskMapper;
    private final ChatMessageService messageService;
    private final ChatMessageFileService messageFileService;

    public ChatGenerationServiceImpl(ChatGenerationTaskMapper taskMapper, ChatMessageService messageService,
                                    ChatMessageFileService messageFileService) {
        this.taskMapper = taskMapper;
        this.messageService = messageService;
        this.messageFileService = messageFileService;
    }

    @Override
    @Transactional
    public ChatGenerationResult start(ChatGenerationCommand command, AiModel model) {
        ChatMessage userMessage;
        if (command.existingUserMessageId() != null) {
            userMessage = messageService.findOwned(command.existingUserMessageId());
            if (!Long.valueOf(command.sessionId()).equals(userMessage.sessionId)
                    || userMessage.role != ChatMessageRole.USER) {
                throw new BusinessException(ChatErrorCode.INVALID_REGENERATE_MESSAGE.errorCode(), "Invalid regenerate source message");
            }
        } else {
            userMessage = messageService.saveUser(command.userId(), command.sessionId(), command.content(), command.requestId());
            messageFileService.bind(userMessage.id, command.userId(), command.fileIds());
        }
        ChatMessage assistantMessage = messageService.saveAssistant(command.userId(), command.sessionId(), model.id,
                command.requestId());
        ChatGenerationTask task = new ChatGenerationTask();
        task.userId = command.userId();
        task.sessionId = command.sessionId();
        task.userMessageId = userMessage.id;
        task.assistantMessageId = assistantMessage.id;
        task.sourceMessageId = command.sourceMessageId();
        task.requestId = command.requestId();
        task.modelId = model.id;
        task.status = ChatGenerationStatus.RUNNING;
        task.startTime = LocalDateTime.now();
        taskMapper.insert(task);
        ChatGenerationResult result = new ChatGenerationResult(task.id, command.userId(), userMessage.id,
                assistantMessage.id, command.requestId(), model.id, command.sourceMessageId());
        messageService.bindTask(result);
        return result;
    }

    @Override
    @Transactional
    public void complete(ChatGenerationResult generation, String content) {
        messageService.complete(generation, content);
        messageService.markUserCompleted(generation);
        finish(generation, ChatGenerationStatus.SUCCESS, null, null);
    }

    @Override
    @Transactional
    public void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage) {
        messageService.fail(generation, content, errorCode, errorMessage);
        messageService.markUserCompleted(generation);
        finish(generation, ChatGenerationStatus.FAILED, errorCode, errorMessage);
    }

    @Override
    @Transactional
    public void interrupt(ChatGenerationResult generation, String content) {
        messageService.interrupt(generation, content);
        messageService.markUserCompleted(generation);
        finish(generation, ChatGenerationStatus.INTERRUPTED, "CHAT_STREAM_INTERRUPTED", "Chat stream interrupted");
    }

    private void finish(ChatGenerationResult generation, ChatGenerationStatus status,
                        String errorCode, String errorMessage) {
        ChatGenerationTask task = new ChatGenerationTask();
        task.id = generation.taskId();
        task.status = status;
        task.errorCode = errorCode;
        task.errorMessage = errorMessage;
        task.endTime = LocalDateTime.now();
        taskMapper.updateById(task);
    }
}
