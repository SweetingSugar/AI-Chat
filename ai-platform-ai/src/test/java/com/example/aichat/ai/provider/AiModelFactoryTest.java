package com.example.aichat.ai.provider;

import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.adapter.OllamaModelProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;

class AiModelFactoryTest {
    @Test
    void routesOllamaSnapshotToOllamaProvider() {
        OllamaModelProvider ollamaProvider = new OllamaModelProvider();
        AiModelFactory factory = new AiModelFactory(List.of(ollamaProvider));
        AiModelConfigSnapshot snapshot = new AiModelConfigSnapshot(
                1L,
                "Ollama",
                AiProviderType.OLLAMA,
                "http://localhost:11434",
                null,
                10,
                60,
                2L,
                "llama3.2",
                8192,
                1024,
                BigDecimal.valueOf(0.7),
                BigDecimal.valueOf(0.9),
                1,
                false
        );

        assertSame(ollamaProvider, factory.get(snapshot));
    }
}
