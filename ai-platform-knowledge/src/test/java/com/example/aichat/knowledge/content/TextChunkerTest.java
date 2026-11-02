package com.example.aichat.knowledge.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextChunkerTest {
    private final TextChunker chunker = new TextChunker();

    @Test
    void keepsShortDocumentAsOneChunk() {
        assertThat(chunker.split("第一段\n\n第二段")).hasSize(1);
    }

    @Test
    void createsOverlappingChunksForLongText() {
        String text = "段落A。".repeat(600);
        List<TextChunk> chunks = chunker.split(text);

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(1).content()).contains(
                chunks.get(0).content().substring(Math.max(0, chunks.get(0).content().length() - 20)));
    }

    @Test
    void returnsNoChunksForBlankText() {
        assertThat(chunker.split(" \n\n ")).isEmpty();
    }
}
