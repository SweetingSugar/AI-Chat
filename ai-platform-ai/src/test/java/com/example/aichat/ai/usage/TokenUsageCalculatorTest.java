package com.example.aichat.ai.usage;

import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.enums.AiMessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageCalculatorTest {
    @Test
    void includesAttachmentEstimatesInInputTokens() {
        AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "question",
                List.of(AiMessageContentDTO.text("extracted"),
                        AiMessageContentDTO.image("image/png", new byte[]{1})));

        AiTokenUsage usage = new TokenUsageCalculator().estimate(List.of(message), "answer");

        assertThat(usage.inputTokens()).isEqualTo(message.estimatedInputTokens());
        assertThat(usage.outputTokens()).isEqualTo(2);
        assertThat(usage.totalTokens()).isEqualTo(usage.inputTokens() + usage.outputTokens());
    }
}
