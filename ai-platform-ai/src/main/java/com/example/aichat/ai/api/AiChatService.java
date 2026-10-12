package com.example.aichat.ai.api;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.stream.AiStreamEvent;
import reactor.core.publisher.Flux;

public interface AiChatService {
    Flux<AiStreamEvent> stream(AiChatRequest request);
}
