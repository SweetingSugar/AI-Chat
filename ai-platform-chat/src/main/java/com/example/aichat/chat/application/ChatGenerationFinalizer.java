package com.example.aichat.chat.application;

import com.example.aichat.ai.dto.response.AiChatResponse;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.service.ChatGenerationService;
import org.springframework.stereotype.Component;

@Component
public class ChatGenerationFinalizer {
    private final ChatGenerationService generationService;

    public ChatGenerationFinalizer(ChatGenerationService generationService) {
        this.generationService = generationService;
    }

    public void complete(ChatGenerationResult generation, String content, AiChatResponse response) {
        generationService.complete(generation, content);
    }

    public void fail(ChatGenerationResult generation, String content, String code, String message) {
        generationService.fail(generation, content, code, message);
    }

    public void interrupt(ChatGenerationResult generation, String content) {
        generationService.interrupt(generation, content);
    }
}
