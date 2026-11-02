package com.example.aichat.knowledge.content;

import java.util.Map;

public record TextChunk(int index, String content, int estimatedTokens, Map<String, Object> metadata) {
    public TextChunk {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
