package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.context.MessageConverter;
import com.example.aichat.ai.dto.request.AiGenerationOptions;
import com.example.aichat.ai.dto.request.AiMessageDTO;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.ModelProvider;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.stream.LangChain4jStreamAdapter;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Component
public class OllamaModelProvider implements ModelProvider {
    @Override
    public boolean supports(AiProviderType providerType) {
        return providerType == AiProviderType.OLLAMA;
    }

    @Override
    public Flux<AiStreamEvent> stream(AiModelConfigSnapshot snapshot, List<AiMessageDTO> messages,
                                      AiGenerationOptions options) {
        return Flux.defer(() -> {
            try {
                var builder = OllamaStreamingChatModel.builder()
                        .baseUrl(snapshot.baseUrl())
                        .modelName(snapshot.modelCode())
                        .timeout(Duration.ofMillis(snapshot.readTimeout() == null ? 120000 : snapshot.readTimeout()));
                if (options != null && options.temperature() != null) {
                    builder.temperature(options.temperature());
                } else if (snapshot.temperature() != null) {
                    builder.temperature(snapshot.temperature().doubleValue());
                }
                if (options != null && options.topP() != null) {
                    builder.topP(options.topP());
                } else if (snapshot.topP() != null) {
                    builder.topP(snapshot.topP().doubleValue());
                }
                return LangChain4jStreamAdapter.adapt(
                        builder.build().chat(MessageConverter.toLangChain4j(messages))
                );
            } catch (RuntimeException error) {
                return Flux.just(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
            }
        });
    }
}
