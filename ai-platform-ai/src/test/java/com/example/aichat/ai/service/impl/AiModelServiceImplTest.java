package com.example.aichat.ai.service.impl;

import com.example.aichat.ai.dto.request.AiModelUpsertRequest;
import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.service.AiProviderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
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
        AiProvider provider = new AiProvider();
        provider.id = 1L;
        provider.providerName = "OpenAI";
        provider.providerType = AiProviderType.OPENAI;
        provider.status = 1;
        when(providerService.getRequired(1L)).thenReturn(provider);
        when(modelMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            AiModel model = invocation.getArgument(0);
            model.id = 2L;
            return 1;
        }).when(modelMapper).insert(any(AiModel.class));

        AiModelUpsertRequest request = new AiModelUpsertRequest(
                1L, "vision-model", "Vision Model", "chat", 8192, 1024,
                BigDecimal.valueOf(0.7), BigDecimal.valueOf(0.9), 1, 1, 0, 1, 0, "");
        AiModelManagementDTO result = new AiModelServiceImpl(modelMapper, providerService).create(request);

        ArgumentCaptor<AiModel> captor = ArgumentCaptor.forClass(AiModel.class);
        verify(modelMapper).insert(captor.capture());
        assertThat(captor.getValue().supportVision).isEqualTo(1);
        assertThat(result.supportVision()).isTrue();
    }
}
