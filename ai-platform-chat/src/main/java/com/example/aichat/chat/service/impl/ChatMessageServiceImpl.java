package com.example.aichat.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.converter.ChatMessageConverter;
import com.example.aichat.chat.dto.ChatMessageQueryDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;
import com.example.aichat.chat.exception.ChatErrorCode;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.chat.mapper.ChatMessageMapper;
import com.example.aichat.chat.mapper.ChatSessionMapper;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.ChatMessageFileService;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.vo.ChatMessageVO;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessageServiceImpl implements ChatMessageService {
    private final ChatMessageMapper messageMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatUserGateway userGateway;
    private final ChatMessageFileService messageFileService;

    public ChatMessageServiceImpl(ChatMessageMapper messageMapper, ChatSessionMapper sessionMapper,
                                  ChatUserGateway userGateway, ChatMessageFileService messageFileService) {
        this.messageMapper = messageMapper;
        this.sessionMapper = sessionMapper;
        this.userGateway = userGateway;
        this.messageFileService = messageFileService;
    }

    @Override
    public PageResult<com.example.aichat.chat.vo.ChatMessageVO> page(Long sessionId, ChatMessageQueryDTO query) {
        CurrentUser user = userGateway.requireCurrentUser();
        requireOwnedSession(user.id(), sessionId);
        QueryWrapper<ChatMessage> wrapper = new QueryWrapper<ChatMessage>()
                .eq("session_id", sessionId).eq("user_id", user.id()).orderByAsc("sequence").orderByAsc("id");
        if (query.status() != null && !query.status().isBlank()) {
            try {
                wrapper.eq("status", ChatMessageStatus.valueOf(query.status().toUpperCase()).name());
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(com.example.aichat.common.error.ErrorCode.VALIDATION_ERROR, "Invalid message status");
            }
        }
        Page<ChatMessage> result = messageMapper.selectPage(new Page<>(query.page(), query.size()), wrapper);
        Map<Long, List<FileMetadataVO>> attachments = attachments(user.id(), result.getRecords());
        return PageResult.from(result, message -> ChatMessageConverter.toVO(message,
                attachments.getOrDefault(message.id, List.of())));
    }

    @Override
    public List<com.example.aichat.chat.vo.ChatMessageVO> listAll(Long sessionId) {
        CurrentUser user = userGateway.requireCurrentUser();
        requireOwnedSession(user.id(), sessionId);
        List<ChatMessage> messages = messageMapper.selectHistory(user.id(), sessionId, null);
        Map<Long, List<FileMetadataVO>> attachments = attachments(user.id(), messages);
        return messages.stream()
                .map(message -> ChatMessageConverter.toVO(message, attachments.getOrDefault(message.id, List.of())))
                .toList();
    }

    @Override
    public ChatMessage findOwned(Long messageId) {
        ChatMessage message = messageMapper.selectOwnedById(userGateway.requireCurrentUser().id(), messageId);
        if (message == null) throw new BusinessException(ChatErrorCode.MESSAGE_NOT_FOUND.errorCode(), "Chat message not found");
        return message;
    }

    @Override
    public ChatMessage findPreviousUser(ChatMessage message) {
        ChatMessage previous = messageMapper.selectPreviousByRole(userGateway.requireCurrentUser().id(),
                message.sessionId, message.sequence, ChatMessageRole.USER);
        if (previous == null) throw new BusinessException(ChatErrorCode.INVALID_REGENERATE_MESSAGE.errorCode(), "No user message found before assistant message");
        return previous;
    }

    @Override
    @Transactional
    public ChatMessage saveUser(Long userId, Long sessionId, String content, String requestId) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.role = ChatMessageRole.USER;
        message.content = content;
        message.sequence = nextSequence(userId, sessionId);
        message.status = ChatMessageStatus.PENDING;
        message.requestId = requestId;
        messageMapper.insert(message);
        touchSession(userId, sessionId);
        return message;
    }

    @Override
    @Transactional
    public ChatMessage saveAssistant(Long userId, Long sessionId, Long modelId, String requestId) {
        ChatMessage message = new ChatMessage();
        message.sessionId = sessionId;
        message.userId = userId;
        message.role = ChatMessageRole.ASSISTANT;
        message.content = "";
        message.sequence = nextSequence(userId, sessionId);
        message.status = ChatMessageStatus.STREAMING;
        message.modelId = modelId;
        message.requestId = requestId;
        messageMapper.insert(message);
        touchSession(userId, sessionId);
        return message;
    }

    @Override
    public void bindTask(ChatGenerationResult generation) {
        messageMapper.update(null, new UpdateWrapper<ChatMessage>()
                .eq("id", generation.assistantMessageId())
                .eq("user_id", generation.userId())
                .set("generation_task_id", generation.taskId()));
    }

    @Override
    public void markUserCompleted(ChatGenerationResult generation) {
        messageMapper.update(null, new UpdateWrapper<ChatMessage>()
                .eq("id", generation.userMessageId())
                .eq("user_id", generation.userId())
                .set("status", ChatMessageStatus.COMPLETED.name()));
    }

    @Override
    public List<ChatMessagePair> context(Long userId, Long sessionId, Long beforeSequence) {
        List<ChatMessage> history = messageMapper.selectHistory(userId, sessionId, beforeSequence).stream()
                .filter(message -> message.role == ChatMessageRole.USER || message.status == ChatMessageStatus.COMPLETED)
                .toList();
        Map<Long, List<Long>> fileIds = messageFileService.findFileIdsByMessageIds(
                history.stream().map(message -> message.id).toList());
        return history.stream()
                .map(message -> new ChatMessagePair(message.role, message.content,
                        fileIds.getOrDefault(message.id, List.of())))
                .toList();
    }

    @Override
    public void append(ChatGenerationResult generation, String content) {
        updateAssistant(generation, content, ChatMessageStatus.STREAMING, null, null);
    }

    @Override
    public void complete(ChatGenerationResult generation, String content) {
        updateAssistant(generation, content, ChatMessageStatus.COMPLETED, null, null);
    }

    @Override
    public void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage) {
        updateAssistant(generation, content, ChatMessageStatus.FAILED, errorCode, errorMessage);
    }

    @Override
    public void interrupt(ChatGenerationResult generation, String content) {
        updateAssistant(generation, content, ChatMessageStatus.INTERRUPTED, "CHAT_STREAM_INTERRUPTED", "Chat stream interrupted");
    }

    private void updateAssistant(ChatGenerationResult generation, String content, ChatMessageStatus status,
                                 String errorCode, String errorMessage) {
        messageMapper.update(null, new UpdateWrapper<ChatMessage>()
                .eq("id", generation.assistantMessageId())
                .eq("user_id", generation.userId())
                .set("content", content == null ? "" : content)
                .set("status", status.name())
                .set("error_code", errorCode)
                .set("error_message", errorMessage));
    }

    private Long nextSequence(Long userId, Long sessionId) {
        Long max = messageMapper.selectMaxSequence(userId, sessionId);
        return (max == null ? 0 : max) + 1;
    }

    private void touchSession(Long userId, Long sessionId) {
        sessionMapper.update(null, new UpdateWrapper<com.example.aichat.chat.entity.ChatSession>()
                .eq("id", sessionId)
                .eq("user_id", userId)
                .set("last_message_time", LocalDateTime.now()));
    }

    private Map<Long, List<FileMetadataVO>> attachments(Long userId, List<ChatMessage> messages) {
        if (messages.isEmpty()) return Map.of();
        return messageFileService.findByMessageIds(userId,
                messages.stream().map(message -> message.id).toList());
    }

    private void requireOwnedSession(Long userId, Long sessionId) {
        if (sessionMapper.selectOwnedById(userId, sessionId) == null) {
            throw new BusinessException(ChatErrorCode.SESSION_NOT_FOUND.errorCode(), "Chat session not found");
        }
    }
}
