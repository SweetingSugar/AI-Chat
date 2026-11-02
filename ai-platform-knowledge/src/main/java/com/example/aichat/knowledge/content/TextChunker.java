package com.example.aichat.knowledge.content;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TextChunker {
    private static final int MAX_TOKENS = 500;
    private static final int OVERLAP_TOKENS = 50;
    private static final int CHARS_PER_TOKEN = 4;

    private final KnowledgeTextNormalizer normalizer;

    public TextChunker() {
        this(new KnowledgeTextNormalizer());
    }

    public TextChunker(KnowledgeTextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public List<TextChunk> split(String text) {
        String normalized = normalizer.normalize(text);
        if (normalized.isEmpty()) {
            return List.of();
        }
        int maxChars = MAX_TOKENS * CHARS_PER_TOKEN;
        int overlapChars = OVERLAP_TOKENS * CHARS_PER_TOKEN;
        if (estimateTokens(normalized) <= MAX_TOKENS) {
            return List.of(new TextChunk(0, normalized, estimateTokens(normalized), Map.of()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxChars);
            if (end < normalized.length()) {
                end = findBoundary(normalized, start, end);
            }
            String content = normalized.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(chunks.size(), content, estimateTokens(content), Map.of()));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlapChars);
        }
        return List.copyOf(chunks);
    }

    private int findBoundary(String text, int start, int end) {
        int minimum = start + (end - start) / 2;
        for (int index = end - 1; index >= minimum; index--) {
            char character = text.charAt(index);
            if (character == '\n' || character == '。' || character == '！'
                    || character == '？' || character == '.' || character == '!' || character == '?') {
                return index + 1;
            }
        }
        return end;
    }

    private int estimateTokens(String text) {
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
