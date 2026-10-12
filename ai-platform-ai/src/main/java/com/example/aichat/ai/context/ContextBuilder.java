package com.example.aichat.ai.context;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ContextBuilder {
    private final TokenBudgetManager tokenBudgetManager = new TokenBudgetManager();

    public List<AiMessageDTO> build(AiChatRequest request, AiModelConfigSnapshot snapshot) {
        Integer output = request.options() != null && request.options().maxOutputTokens() != null ? request.options().maxOutputTokens() : snapshot.maxOutputTokens();
        int budget = Math.max(0, (snapshot.contextWindow() == null ? 8192 : snapshot.contextWindow()) - (output == null ? 1024 : output));
        return tokenBudgetManager.trim(request.messages(), budget);
    }
}
