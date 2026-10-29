package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TokenBudgetManager {
    public List<AiMessageDTO> trim(List<AiMessageDTO> messages, int tokenBudget) {
        List<AiMessageDTO> result = new ArrayList<>();
        int remaining = Math.max(tokenBudget, 0);
        AiMessageDTO system = messages.stream().filter(message -> message.role().name().equals("SYSTEM")).findFirst().orElse(null);
        if (system != null) {
            result.add(system);
            remaining -= system.estimatedInputTokens();
        }
        List<AiMessageDTO> reversed = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiMessageDTO message = messages.get(index);
            if (message == system) continue;
            int cost = message.estimatedInputTokens();
            if (cost > remaining) {
                if (index == messages.size() - 1 && !message.contents().isEmpty()) {
                    throw new BusinessException(ErrorCode.AI_FILE_CONTENT_TOO_LARGE,
                            "File content exceeds the available context");
                }
                break;
            }
            reversed.add(message);
            remaining -= cost;
        }
        Collections.reverse(reversed);
        result.addAll(reversed);
        return List.copyOf(result);
    }
}
