package com.example.aichat.ai.usage;

import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TokenUsageCalculator {
    public AiTokenUsage estimate(List<AiMessageDTO> input, String output) {
        int inputTokens = input.stream().mapToInt(AiMessageDTO::estimatedInputTokens).sum();
        int outputTokens = tokens(output);
        return new AiTokenUsage(inputTokens, outputTokens, inputTokens + outputTokens, "ESTIMATED");
    }

    private int tokens(String value) {
        return value == null || value.isEmpty() ? 0 : (value.length() + 3) / 4;
    }
}
