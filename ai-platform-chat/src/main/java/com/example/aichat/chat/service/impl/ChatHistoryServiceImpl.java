package com.example.aichat.chat.service.impl;

import com.example.aichat.chat.api.ChatHistoryMessage;
import com.example.aichat.chat.api.ChatHistoryQuery;
import com.example.aichat.chat.api.ChatHistoryApi;
import com.example.aichat.chat.converter.ChatMessageConverter;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.exception.ChatErrorCode;
import com.example.aichat.chat.mapper.ChatMessageMapper;
import com.example.aichat.chat.mapper.ChatSessionMapper;
import com.example.aichat.chat.service.ChatHistoryService;
import com.example.aichat.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatHistoryServiceImpl implements ChatHistoryService, ChatHistoryApi {
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;

    public ChatHistoryServiceImpl(ChatSessionMapper sessionMapper, ChatMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    @Override
    public ChatHistoryQuery query(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectOwnedById(userId, sessionId);
        if (session == null) throw new BusinessException(ChatErrorCode.SESSION_NOT_FOUND.errorCode(), "Chat session not found");
        List<ChatHistoryMessage> messages = messageMapper.selectHistory(userId, sessionId, null).stream()
                .map(ChatMessageConverter::toHistory).toList();
        return new ChatHistoryQuery(session.id, session.title, session.defaultModelId, messages);
    }
}
