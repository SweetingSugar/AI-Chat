package com.example.aichat.ai.core;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiModelType;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.EmbeddingProvider;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultAiEmbeddingServiceTest {
    @Mock
    private AiModelService modelService;
    @Mock
    private AiProviderService providerService;
    @Mock
    private EmbeddingProvider provider;

    private DefaultAiEmbeddingService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAiEmbeddingService(modelService, providerService, List.of(provider));
    }

    @Test
    void returnsEmptyResultWithoutCallingModelService() {
        assertThat(service.embed(8L, List.of())).isEmpty();
    }

    @Test
    void routesToProviderAndValidatesReturnedDimension() {
        AiModel model = embeddingModel(8L, 3);
        when(modelService.requireEnabledEmbedding(8L)).thenReturn(model);
        when(providerService.requireEnabled(1L)).thenReturn(enabledProvider());
        when(provider.supports(AiProviderType.OPENAI)).thenReturn(true);
        when(provider.embed(any(), eq(List.of("a", "b"))))
                .thenReturn(List.of(new float[]{1, 0, 0}, new float[]{0, 1, 0}));

        assertThat(service.embed(8L, List.of("a", "b"))).hasSize(2);
        verify(provider).embed(any(), eq(List.of("a", "b")));
    }

    @Test
    void rejectsProviderResultWithWrongDimension() {
        AiModel model = embeddingModel(8L, 3);
        when(modelService.requireEnabledEmbedding(8L)).thenReturn(model);
        when(providerService.requireEnabled(1L)).thenReturn(enabledProvider());
        when(provider.supports(AiProviderType.OPENAI)).thenReturn(true);
        when(provider.embed(any(), any())).thenReturn(List.of(new float[]{1, 0}));

        assertThatThrownBy(() -> service.embed(8L, List.of("a")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("KNOWLEDGE_EMBEDDING_DIMENSION_MISMATCH");
    }

    private AiModel embeddingModel(Long id, int dimension) {
        AiModel model = new AiModel();
        model.id = id;
        model.providerId = 1L;
        model.modelCode = "text-embedding";
        model.modelType = AiModelType.EMBEDDING.name();
        model.embeddingDimension = dimension;
        model.status = 1;
        return model;
    }

    private AiProvider enabledProvider() {
        AiProvider provider = new AiProvider();
        provider.id = 1L;
        provider.providerName = "OpenAI";
        provider.providerType = AiProviderType.OPENAI;
        provider.baseUrl = "https://example.test/v1";
        provider.status = 1;
        return provider;
    }
}
