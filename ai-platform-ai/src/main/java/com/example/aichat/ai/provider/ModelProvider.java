package com.example.aichat.ai.provider;

import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.tool.AiToolContext;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ModelProvider {
    boolean supports(AiProviderType providerType);

    Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages,
                               AiGenerationOptions options);

    default Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages,
                                       AiGenerationOptions options, AiToolContext toolContext) {
        if (toolContext != null) {
            return Flux.just(AiStreamEvent.error("AI_TOOL_UNSUPPORTED",
                    "The selected AI provider does not support tools"));
        }
        return stream(snapshot, messages, options);
    }
}
