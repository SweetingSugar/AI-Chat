package com.example.aichat.chat.service;

import com.example.aichat.chat.dto.ChatMessageQueryDTO;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.chat.vo.ChatMessageVO;

import java.util.List;

public interface ChatMessageService {
    PageResult<ChatMessageVO> page(Long sessionId, ChatMessageQueryDTO query);

    List<ChatMessageVO> listAll(Long sessionId);

    ChatMessage findOwned(Long messageId);

    ChatMessage findPreviousUser(ChatMessage message);

    ChatMessage saveUser(Long userId, Long sessionId, String content, String requestId);

    ChatMessage saveAssistant(Long userId, Long sessionId, Long modelId, String requestId);

    void bindTask(ChatGenerationResult generation);

    void markUserCompleted(ChatGenerationResult generation);

    List<ChatMessagePair> context(Long userId, Long sessionId, Long beforeSequence);

    void append(ChatGenerationResult generation, String content);

    void complete(ChatGenerationResult generation, String content);

    void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage);

    void interrupt(ChatGenerationResult generation, String content);
}
