package com.example.aichat.ai.core;

import com.example.aichat.ai.dto.response.AiChatResponse;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import org.springframework.stereotype.Component;

@Component
public class AiResponseAssembler {
    public AiChatResponse complete(String requestId, AiModelConfigSnapshot snapshot, AiTokenUsage usage) {
        return new AiChatResponse(requestId, snapshot.providerId(), snapshot.modelId(), snapshot.modelCode(), usage);
    }
}
