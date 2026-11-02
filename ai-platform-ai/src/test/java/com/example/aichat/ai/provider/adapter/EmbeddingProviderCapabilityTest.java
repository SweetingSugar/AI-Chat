package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.enums.AiProviderType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingProviderCapabilityTest {
    private final OpenAiEmbeddingProvider openAiProvider = new OpenAiEmbeddingProvider((AiCredentialResolver) ignored -> "key");
    private final OllamaEmbeddingProvider ollamaProvider = new OllamaEmbeddingProvider();

    @Test
    void openAiCompatibleProviderSupportsOpenAiDeepSeekAndQwen() {
        assertThat(openAiProvider.supports(AiProviderType.OPENAI)).isTrue();
        assertThat(openAiProvider.supports(AiProviderType.DEEPSEEK)).isTrue();
        assertThat(openAiProvider.supports(AiProviderType.QWEN)).isTrue();
        assertThat(openAiProvider.supports(AiProviderType.OLLAMA)).isFalse();
    }

    @Test
    void ollamaProviderSupportsOnlyOllama() {
        assertThat(ollamaProvider.supports(AiProviderType.OLLAMA)).isTrue();
        assertThat(ollamaProvider.supports(AiProviderType.OPENAI)).isFalse();
        assertThat(ollamaProvider.supports(AiProviderType.DEEPSEEK)).isFalse();
        assertThat(ollamaProvider.supports(AiProviderType.QWEN)).isFalse();
    }
}
