package com.example.aichat.chat.controller;

import com.example.aichat.ai.api.AiChatService;
import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.stream.AiStreamEvent;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Temporary endpoint for verifying an AI provider configuration end to end.
 */
@RestController
@RequestMapping("/api/chat/test")
public class AiChatTestController {
    private final AiChatService aiChatService;

    public AiChatTestController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AiStreamEvent>> stream(@RequestBody AiChatRequest request) {
        return aiChatService.stream(request)
                .map(event -> ServerSentEvent.builder(event)
                        .event(event.type().name().toLowerCase())
                        .build());
    }
}
