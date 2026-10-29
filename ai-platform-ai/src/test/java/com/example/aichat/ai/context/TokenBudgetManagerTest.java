package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageContentDTO;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBudgetManagerTest {
    @Test
    void countsTextAttachmentAndImageCostWhenTrimming() {
        AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "question",
                List.of(AiMessageContentDTO.text("extracted pdf text"),
                        AiMessageContentDTO.image("image/png", new byte[]{1})));

        assertThat(new TokenBudgetManager().trim(List.of(message), message.estimatedInputTokens()))
                .containsExactly(message);
    }

    @Test
    void rejectsCurrentMessageWhenAttachmentCannotFit() {
        AiMessageDTO message = new AiMessageDTO(AiMessageRole.USER, "question",
                List.of(AiMessageContentDTO.image("image/png", new byte[]{1})));

        assertThatThrownBy(() -> new TokenBudgetManager().trim(List.of(message), 1))
                .hasMessage("File content exceeds the available context");
    }

    @Test
    void keepsNewestMessagesWithinTheAvailableBudget() {
        AiMessageDTO first = new AiMessageDTO(AiMessageRole.USER, "old message");
        AiMessageDTO newest = new AiMessageDTO(AiMessageRole.USER, "new message");

        assertThat(new TokenBudgetManager().trim(List.of(first, newest), newest.estimatedInputTokens()))
                .containsExactly(newest);
    }
}
