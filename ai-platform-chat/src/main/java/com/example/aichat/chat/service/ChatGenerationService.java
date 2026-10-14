package com.example.aichat.chat.service;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;

public interface ChatGenerationService {
    ChatGenerationResult start(ChatGenerationCommand command, AiModel model);

    void complete(ChatGenerationResult generation, String content);

    void fail(ChatGenerationResult generation, String content, String errorCode, String errorMessage);

    void interrupt(ChatGenerationResult generation, String content);
}
