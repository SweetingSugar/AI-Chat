package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiMessageDTO;

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
            remaining -= estimate(system.content());
        }
        List<AiMessageDTO> reversed = new ArrayList<>();
        for (int index = messages.size() - 1; index >= 0; index--) {
            AiMessageDTO message = messages.get(index);
            if (message == system) continue;
            int cost = estimate(message.content());
            if (cost > remaining) break;
            reversed.add(message);
            remaining -= cost;
        }
        Collections.reverse(reversed);
        result.addAll(reversed);
        return List.copyOf(result);
    }

    private int estimate(String value) {
        return (value.length() + 3) / 4;
    }
}
