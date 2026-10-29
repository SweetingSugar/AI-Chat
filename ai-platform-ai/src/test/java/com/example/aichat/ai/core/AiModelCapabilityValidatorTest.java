package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiModelCapabilityValidatorTest {
    private final AiModelCapabilityValidator validator = new AiModelCapabilityValidator();

    @Test
    void acceptsTextAndPdfForNonVisionModel() {
        assertThatCode(() -> validator.validate(textRequest(), snapshot(false)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsImageForNonVisionModel() {
        assertThatThrownBy(() -> validator.validate(imageRequest(), snapshot(false)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("The selected model does not support image input");
    }

    @Test
    void acceptsImageForVisionModel() {
        assertThatCode(() -> validator.validate(imageRequest(), snapshot(true)))
                .doesNotThrowAnyException();
    }

    private AiChatRequest textRequest() {
        return new AiChatRequest("request", 1L, 2L, 3L,
                List.of(new AiMessageDTO(AiMessageRole.USER, "read this")), null);
    }

    private AiChatRequest imageRequest() {
        return new AiChatRequest("request", 1L, 2L, 3L,
                List.of(new AiMessageDTO(AiMessageRole.USER, "describe this",
                        List.of(AiMessageContentDTO.image("image/png", new byte[]{1})))), null);
    }

    private AiModelConfigSnapshot snapshot(boolean supportVision) {
        return new AiModelConfigSnapshot(1L, "test", AiProviderType.OPENAI, "http://localhost", null,
                10, 60, 2L, "model", 8192, 1024, null, null, 1, supportVision);
    }
}
