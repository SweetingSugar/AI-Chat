package com.example.aichat.ai.core;

import com.example.aichat.ai.api.AiChatService;
import com.example.aichat.ai.context.ContextBuilder;
import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.entity.AiCallLog;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.provider.AiModelFactory;
import com.example.aichat.ai.provider.AiModelRouter;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.stream.AiStreamEventType;
import com.example.aichat.ai.usage.AiUsageRecorder;
import com.example.aichat.ai.usage.TokenUsageCalculator;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DefaultAiChatService implements AiChatService {
    private final AiRequestValidator validator;
    private final AiModelRouter router;
    private final AiModelFactory factory;
    private final ContextBuilder contextBuilder;
    private final AiUsageRecorder usageRecorder;
    private final TokenUsageCalculator tokenUsageCalculator;
    private final AiResponseAssembler responseAssembler;

    public DefaultAiChatService(AiRequestValidator validator, AiModelRouter router, AiModelFactory factory, ContextBuilder contextBuilder,
                                AiUsageRecorder usageRecorder, TokenUsageCalculator tokenUsageCalculator, AiResponseAssembler responseAssembler) {
        this.validator = validator;
        this.router = router;
        this.factory = factory;
        this.contextBuilder = contextBuilder;
        this.usageRecorder = usageRecorder;
        this.tokenUsageCalculator = tokenUsageCalculator;
        this.responseAssembler = responseAssembler;
    }

    @Override
    public Flux<AiStreamEvent> stream(AiChatRequest request) {
        return Flux.defer(() -> {
            validator.validate(request);
            String requestId = request.requestId() == null || request.requestId().isBlank() ? UUID.randomUUID().toString() : request.requestId();
            AiModelConfigSnapshot snapshot = router.route(request.options());
            AiCallLog log = usageRecorder.start(requestId, request, snapshot);
            StringBuilder output = new StringBuilder();
            AtomicBoolean terminal = new AtomicBoolean();
            Flux<AiStreamEvent> upstream = factory.get(snapshot).stream(snapshot, contextBuilder.build(request, snapshot), request.options())
                    .doOnNext(event -> {
                        if (event.type() == AiStreamEventType.DELTA) output.append(event.delta());
                        if (event.type() == AiStreamEventType.ERROR && terminal.compareAndSet(false, true))
                            usageRecorder.failed(log, event.code(), event.message());
                    });
            return upstream.concatWith(Flux.defer(() -> {
                        if (terminal.get()) return Flux.empty();
                        terminal.set(true);
                        AiTokenUsage usage = tokenUsageCalculator.estimate(request.messages(), output.toString());
                        usageRecorder.success(log, usage);
                        return Flux.just(AiStreamEvent.complete(responseAssembler.complete(requestId, snapshot, usage)));
                    })).doOnCancel(() -> {
                        if (terminal.compareAndSet(false, true)) usageRecorder.cancelled(log);
                    })
                    .onErrorResume(error -> {
                        if (terminal.compareAndSet(false, true))
                            usageRecorder.failed(log, "AI_PROVIDER_ERROR", "AI provider request failed");
                        return Flux.just(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
                    });
        });
    }
}
