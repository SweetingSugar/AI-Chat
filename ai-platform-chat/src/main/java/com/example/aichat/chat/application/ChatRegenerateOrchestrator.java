package com.example.aichat.chat.application;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ChatRegenerateOrchestrator {
    private final ChatStreamOrchestrator streamOrchestrator;

    public ChatRegenerateOrchestrator(ChatStreamOrchestrator streamOrchestrator) {
        this.streamOrchestrator = streamOrchestrator;
    }

    public Flux<ChatStreamEventVO> stream(ChatGenerationCommand command, AiModel model) {
        return streamOrchestrator.stream(command, model);
    }
}
