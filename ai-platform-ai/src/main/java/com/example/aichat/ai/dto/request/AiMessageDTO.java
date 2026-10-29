package com.example.aichat.ai.dto.request;

import com.example.aichat.ai.enums.AiMessageRole;

import java.util.List;

public record AiMessageDTO(AiMessageRole role, String content,
                           List<AiMessageContentDTO> contents) {
    public AiMessageDTO(AiMessageRole role, String content) {
        this(role, content, List.of());
    }

    public AiMessageDTO {
        contents = contents == null ? List.of() : List.copyOf(contents);
    }

    public boolean hasImageContent() {
        return contents.stream().anyMatch(item -> item.type() == AiMessageContentType.IMAGE);
    }

    public int estimatedInputTokens() {
        int textTokens = content == null ? 0 : (content.length() + 3) / 4;
        int contentTokens = contents.stream().mapToInt(item -> item.type() == AiMessageContentType.IMAGE
                ? 1024 : item.text() == null ? 0 : (item.text().length() + 3) / 4).sum();
        return textTokens + contentTokens;
    }
}
