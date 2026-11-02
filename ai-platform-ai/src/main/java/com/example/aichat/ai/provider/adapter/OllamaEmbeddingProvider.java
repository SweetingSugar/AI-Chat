package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.EmbeddingProvider;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {
    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.OLLAMA;
    }

    @Override
    public List<float[]> embed(AiModelConfigSnapshot snapshot, List<String> texts) {
        var builder = OllamaEmbeddingModel.builder()
                .baseUrl(snapshot.baseUrl())
                .modelName(snapshot.modelCode())
                .timeout(Duration.ofMillis(snapshot.readTimeout() == null ? 120000 : snapshot.readTimeout()));
        if (snapshot.embeddingDimension() != null) {
            builder.dimensions(snapshot.embeddingDimension());
        }
        Response<List<Embedding>> response = builder.build().embedAll(
                texts.stream().map(TextSegment::from).toList());
        return response.content().stream().map(Embedding::vector).toList();
    }
}
