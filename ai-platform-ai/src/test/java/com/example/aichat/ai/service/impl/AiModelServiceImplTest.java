package com.example.aichat.ai.service.impl;

import com.example.aichat.ai.dto.request.AiModelUpsertRequest;
import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiModelServiceImplTest {
    @Mock
    private AiModelMapper modelMapper;
    @Mock
    private AiProviderService providerService;

    @Test
    void persistsAndReturnsVisionCapability() {
        AiProvider provider = enabledProvider();
        when(providerService.getRequired(1L)).thenReturn(provider);
        when(modelMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            AiModel model = invocation.getArgument(0);
            model.id = 2L;
            return 1;
        }).when(modelMapper).insert(any(AiModel.class));

        AiModelManagementDTO result = service().create(request("chat", null));

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).insert(captor.capture());
        assertThat(captor.getValue().supportVision).isEqualTo(1);
        assertThat(result.supportVision()).isTrue();
    }

    @Test
    void rejectsEmbeddingModelWithoutDimension() {
        when(providerService.getRequired(1L)).thenReturn(enabledProvider());
        when(modelMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().create(request("embedding", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void rejectsDimensionOnChatModel() {
        when(providerService.getRequired(1L)).thenReturn(enabledProvider());
        when(modelMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().create(request("chat", 1536)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode.code")
                .isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void availableChatModelsExcludeEmbeddingModels() {
        AiModel chat = model(1L, "CHAT");
        AiModel embedding = model(2L, "EMBEDDING");
        when(modelMapper.selectList(any())).thenReturn(List.of(chat, embedding));
        when(providerService.getRequired(any())).thenReturn(enabledProvider());

        assertThat(service().listAvailableModels()).extracting(AiModelDTO::id).containsExactly(1L);
    }

    private AiModelServiceImpl service() {
        return new AiModelServiceImpl(modelMapper, providerService, List.of());
    }

    private AiModelUpsertRequest request(String modelType, Integer embeddingDimension) {
        return new AiModelUpsertRequest(
                1L, "model-code", "Model", modelType, embeddingDimension, 8192, 1024,
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.9), 1, 1, 0, 1, 0, "");
    }

    private AiProvider enabledProvider() {
        AiProvider provider = new AiProvider();
        provider.id = 1L;
        provider.providerName = "OpenAI";
        provider.providerType = AiProviderType.OPENAI;
        provider.status = 1;
        return provider;
    }

    private AiModel model(Long id, String modelType) {
        AiModel model = new AiModel();
        model.id = id;
        model.providerId = 1L;
        model.modelType = modelType;
        model.status = 1;
        return model;
    }
}
