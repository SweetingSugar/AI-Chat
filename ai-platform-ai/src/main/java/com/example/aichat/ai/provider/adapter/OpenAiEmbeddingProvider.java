package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.config.AiCredentialResolver;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.EmbeddingProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {
    private final AiCredentialResolver credentialResolver;

    public OpenAiEmbeddingProvider(AiCredentialResolver credentialResolver) {
        this.credentialResolver = credentialResolver;
    }

    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.OPENAI
                || providerType == AiProviderType.DEEPSEEK
                || providerType == AiProviderType.QWEN;
    }

    @Override
    public List<float[]> embed(AiModelConfigSnapshot snapshot, List<String> texts) {
        var builder = OpenAiEmbeddingModel.builder()
                .baseUrl(snapshot.baseUrl())
                .apiKey(credentialResolver.resolve(snapshot.apiKeyCiphertext()))
                .modelName(snapshot.modelCode())
                .timeout(Duration.ofMillis(snapshot.readTimeout() == null ? 120000 : snapshot.readTimeout()));
        if (snapshot.embeddingDimension() != null && snapshot.providerId() != 4L) {
            builder.dimensions(snapshot.embeddingDimension());
        }
        Response<List<Embedding>> response = builder.build().embedAll(
                texts.stream().map(TextSegment::from).toList());
        return response.content().stream().map(Embedding::vector).toList();
    }
}
