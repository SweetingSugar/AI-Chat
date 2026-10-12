package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageDTO;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

public final class MessageConverter {
    private MessageConverter() {
    }

    public static List<ChatMessage> toLangChain4j(List<AiMessageDTO> messages) {
        return messages.stream().map(message -> switch (message.role()) {
            case SYSTEM -> SystemMessage.from(message.content());
            case USER -> UserMessage.from(message.content());
            case ASSISTANT -> AiMessage.from(message.content());
        }).toList();
    }
}
