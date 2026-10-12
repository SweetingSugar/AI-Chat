package com.example.aichat.ai.usage;

import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiMessageRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenUsageCalculatorTest {

    private final TokenUsageCalculator calculator = new TokenUsageCalculator();

    @Test
    void estimatesInputAndOutputTokensSeparately() {
        var input = List.of(new AiMessageDTO(AiMessageRole.USER, "12345678"));

        var usage = calculator.estimate(input, "1234");

        assertThat(usage.inputTokens()).isEqualTo(2);
        assertThat(usage.outputTokens()).isEqualTo(1);
        assertThat(usage.totalTokens()).isEqualTo(3);
        assertThat(usage.source()).isEqualTo("ESTIMATED");
    }
}
