package com.example.aichat.ai.provider;

import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.stream.AiStreamEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ModelProvider {
    boolean supports(AiProviderType providerType);

    Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages, AiGenerationOptions options);
}
