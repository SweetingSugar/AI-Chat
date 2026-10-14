package com.example.aichat.chat.service;

import com.example.aichat.chat.dto.CreateSessionDTO;
import com.example.aichat.chat.dto.UpdateSessionDTO;
import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.vo.ChatSessionDetailVO;
import com.example.aichat.chat.vo.ChatSessionVO;
import com.example.aichat.common.api.PageResult;

public interface ChatSessionService {
    ChatSessionVO create(CreateSessionDTO request);

    PageResult<ChatSessionVO> page(int page, int size);

    ChatSessionDetailVO detail(Long sessionId);

    ChatSessionVO update(Long sessionId, UpdateSessionDTO request);

    ChatSessionVO pin(Long sessionId, boolean pinned);

    void delete(Long sessionId);

    ChatSession requireOwned(Long sessionId);
}
