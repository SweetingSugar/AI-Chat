package com.example.aichat.chat.service;

import com.example.aichat.chat.dto.ChatRegenerateDTO;
import com.example.aichat.chat.dto.ChatRequestDTO;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import reactor.core.publisher.Flux;

public interface ChatService {
    Flux<ChatStreamEventVO> stream(Long sessionId, ChatRequestDTO request);

    Flux<ChatStreamEventVO> regenerate(Long sessionId, ChatRegenerateDTO request);
}
