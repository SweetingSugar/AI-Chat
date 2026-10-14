package com.example.aichat.chat.gateway;

import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import reactor.core.publisher.Flux;

import java.util.List;

public interface AiChatGateway {
    Flux<AiStreamEvent> stream(ChatGenerationCommand command, ChatGenerationResult generation,
                               List<ChatMessagePair> messages);
}
