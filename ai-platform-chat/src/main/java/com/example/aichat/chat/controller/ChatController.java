package com.example.aichat.chat.controller;

import com.example.aichat.chat.dto.ChatRegenerateDTO;
import com.example.aichat.chat.dto.ChatRequestDTO;
import com.example.aichat.chat.service.ChatService;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat/sessions/{sessionId}")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEventVO>> stream(@PathVariable("sessionId") Long sessionId,
                                                            @Valid @RequestBody ChatRequestDTO request) {
        return toSse(chatService.stream(sessionId, request));
    }

    @PostMapping(value = "/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatStreamEventVO>> regenerate(@PathVariable("sessionId") Long sessionId,
                                                                @Valid @RequestBody ChatRegenerateDTO request) {
        return toSse(chatService.regenerate(sessionId, request));
    }

    private Flux<ServerSentEvent<ChatStreamEventVO>> toSse(Flux<ChatStreamEventVO> events) {
        return events.map(event -> ServerSentEvent.builder(event)
                .event(event.type().name().toLowerCase())
                .id(event.requestId())
                .build());
    }
}
