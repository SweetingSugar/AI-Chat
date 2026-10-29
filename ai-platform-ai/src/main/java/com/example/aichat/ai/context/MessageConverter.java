package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageContentType;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class MessageConverter {
    private MessageConverter() {
    }

    public static List<ChatMessage> toLangChain4j(List<AiMessageDTO> messages) {
        return messages.stream().map(message -> switch (message.role()) {
            case SYSTEM -> SystemMessage.from(message.content());
            case USER -> toUserMessage(message);
            case ASSISTANT -> AiMessage.from(message.content());
        }).toList();
    }

    private static UserMessage toUserMessage(AiMessageDTO message) {
        if (message.contents().isEmpty()) return UserMessage.from(message.content());
        List<Content> contents = new ArrayList<>();
        if (message.content() != null && !message.content().isBlank()) {
            contents.add(TextContent.from(message.content()));
        }
        for (AiMessageContentDTO content : message.contents()) {
            if (content.type() == AiMessageContentType.TEXT) {
                contents.add(TextContent.from(content.text()));
                continue;
            }
            Image image = Image.builder()
                    .base64Data(Base64.getEncoder().encodeToString(content.data()))
                    .mimeType(content.mediaType())
                    .build();
            contents.add(ImageContent.from(image));
        }
        return UserMessage.from(contents);
    }
}
