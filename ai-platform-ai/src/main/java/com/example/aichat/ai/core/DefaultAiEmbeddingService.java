package com.example.aichat.ai.core;

import com.example.aichat.ai.api.AiEmbeddingService;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.EmbeddingProvider;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DefaultAiEmbeddingService implements AiEmbeddingService {
    private final AiModelService modelService;
    private final AiProviderService providerService;
    private final List<EmbeddingProvider> providers;

    public DefaultAiEmbeddingService(AiModelService modelService, AiProviderService providerService,
                                     List<EmbeddingProvider> providers) {
        this.modelService = modelService;
        this.providerService = providerService;
        this.providers = List.copyOf(providers);
    }

    @Override
    public List<float[]> embed(Long modelId, List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        AiModel model = modelService.requireEnabledEmbedding(modelId);
        AiProvider provider = providerService.requireEnabled(model.providerId);
        AiModelConfigSnapshot snapshot = AiModelConfigSnapshot.from(provider, model);
        EmbeddingProvider embeddingProvider = providers.stream()
                .filter(candidate -> candidate.supports(snapshot.providerType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE,
                        "No embedding provider supports " + snapshot.providerType()));

        List<float[]> vectors;
        try {
            vectors = embeddingProvider.embed(snapshot, texts);
        } catch (RuntimeException error) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE,
                    "Embedding provider request failed", error);
        }
        if (vectors == null || vectors.size() != texts.size()) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE,
                    "Embedding provider returned an unexpected number of vectors");
        }
        for (float[] vector : vectors) {
            if (vector == null || vector.length != model.embeddingDimension) {
                throw new BusinessException(ErrorCode.KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH,
                        "Embedding vector dimension does not match model configuration");
            }
        }
        return vectors;
    }
}
