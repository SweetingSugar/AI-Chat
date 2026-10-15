package com.example.aichat.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.chat.converter.ChatSessionConverter;
import com.example.aichat.chat.dto.CreateSessionDTO;
import com.example.aichat.chat.dto.UpdateSessionDTO;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.enums.ChatSessionStatus;
import com.example.aichat.chat.exception.ChatErrorCode;
import com.example.aichat.chat.gateway.AiModelGateway;
import com.example.aichat.chat.gateway.ChatUserGateway;
import com.example.aichat.chat.mapper.ChatSessionMapper;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.service.ChatSessionService;
import com.example.aichat.chat.vo.ChatSessionDetailVO;
import com.example.aichat.chat.vo.ChatSessionVO;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {
    private final ChatSessionMapper sessionMapper;
    private final ChatUserGateway userGateway;
    private final AiModelGateway modelGateway;
    private final ChatMessageService messageService;

    public ChatSessionServiceImpl(ChatSessionMapper sessionMapper,
                                  ChatUserGateway userGateway, AiModelGateway modelGateway,
                                  ChatMessageService messageService) {
        this.sessionMapper = sessionMapper;
        this.userGateway = userGateway;
        this.modelGateway = modelGateway;
        this.messageService = messageService;
    }

    @Override
    @Transactional
    public ChatSessionVO create(CreateSessionDTO request) {
        CurrentUser user = userGateway.requireCurrentUser();
        Long defaultModelId = modelGateway.resolve(request.defaultModelId(), null).id;
        ChatSession session = new ChatSession();
        session.userId = user.id();
        session.title = StringUtils.hasText(request.title()) ? request.title().trim() : "新会话";
        session.defaultModelId = defaultModelId;
        session.pinned = 0;
        session.status = ChatSessionStatus.ACTIVE;
        sessionMapper.insert(session);
        return ChatSessionConverter.toVO(session);
    }

    @Override
    public PageResult<ChatSessionVO> page(int page, int size) {
        CurrentUser user = userGateway.requireCurrentUser();
        Page<ChatSession> result = sessionMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ChatSession>().eq("user_id", user.id())
                        .eq("status", ChatSessionStatus.ACTIVE.name())
                        .orderByDesc("pinned")
                        .orderByDesc("last_message_time")
                        .orderByDesc("id"));
        return PageResult.from(result, ChatSessionConverter::toVO);
    }

    @Override
    public ChatSessionDetailVO detail(Long sessionId) {
        ChatSession session = requireOwned(sessionId);
        return new ChatSessionDetailVO(ChatSessionConverter.toVO(session), messageService.listAll(session.id));
    }

    @Override
    @Transactional
    public ChatSessionVO update(Long sessionId, UpdateSessionDTO request) {
        ChatSession session = requireOwned(sessionId);
        if (request.defaultModelId() != null) modelGateway.resolve(request.defaultModelId(), null);
        if (request.title() != null) session.title = StringUtils.hasText(request.title()) ? request.title().trim() : "新会话";
        session.defaultModelId = request.defaultModelId();
        sessionMapper.updateById(session);
        return ChatSessionConverter.toVO(session);
    }

    @Override
    @Transactional
    public ChatSessionVO pin(Long sessionId, boolean pinned) {
        ChatSession session = requireOwned(sessionId);
        session.pinned = pinned ? 1 : 0;
        sessionMapper.updateById(session);
        return ChatSessionConverter.toVO(session);
    }

    @Override
    @Transactional
    public void delete(Long sessionId) {
        ChatSession session = requireOwned(sessionId);
        sessionMapper.update(null, new UpdateWrapper<ChatSession>()
                .eq("id", session.id)
                .eq("user_id", session.userId)
                .set("status", ChatSessionStatus.ARCHIVED.name())
                .set("deleted", 1));
    }

    @Override
    public ChatSession requireOwned(Long sessionId) {
        CurrentUser user = userGateway.requireCurrentUser();
        ChatSession session = sessionMapper.selectOwnedById(user.id(), sessionId);
        if (session == null || session.status == ChatSessionStatus.ARCHIVED) {
            throw new BusinessException(ChatErrorCode.SESSION_NOT_FOUND.errorCode(), "Chat session not found");
        }
        return session;
    }
}
