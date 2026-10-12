package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestValidatorTest {

    private final AiRequestValidator validator = new AiRequestValidator();

    @Test
    void acceptsAChatRequestWithNonBlankMessages() {
        var request = new AiChatRequest(null, 1L, 2L, 3L,
                List.of(new AiMessageDTO(AiMessageRole.USER, "hello")), null);

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankMessageContentBeforeAnyModelCall() {
        var request = new AiChatRequest(null, 1L, null, null,
                List.of(new AiMessageDTO(AiMessageRole.USER, "   ")), null);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
