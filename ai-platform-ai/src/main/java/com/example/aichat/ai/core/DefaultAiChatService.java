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
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.ai.tool.AiToolProvider;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.ai.usage.AiUsageRecorder;
import com.example.aichat.ai.usage.TokenUsageCalculator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DefaultAiChatService implements AiChatService {
    private final AiRequestValidator validator;
    private final AiModelCapabilityValidator capabilityValidator;
    private final AiModelRouter router;
    private final AiModelFactory factory;
    private final ContextBuilder contextBuilder;
    private final AiUsageRecorder usageRecorder;
    private final TokenUsageCalculator tokenUsageCalculator;
    private final AiResponseAssembler responseAssembler;
    private final List<AiToolProvider> toolProviders;

    public DefaultAiChatService(AiRequestValidator validator, AiModelCapabilityValidator capabilityValidator,
                                AiModelRouter router, AiModelFactory factory, ContextBuilder contextBuilder,
                                AiUsageRecorder usageRecorder, TokenUsageCalculator tokenUsageCalculator,
                                AiResponseAssembler responseAssembler) {
        this(validator, capabilityValidator, router, factory, contextBuilder, usageRecorder,
                tokenUsageCalculator, responseAssembler, List.of());
    }

    @Autowired
    public DefaultAiChatService(AiRequestValidator validator, AiModelCapabilityValidator capabilityValidator,
                                AiModelRouter router, AiModelFactory factory, ContextBuilder contextBuilder,
                                AiUsageRecorder usageRecorder, TokenUsageCalculator tokenUsageCalculator,
                                AiResponseAssembler responseAssembler, List<AiToolProvider> toolProviders) {
        this.validator = validator;
        this.capabilityValidator = capabilityValidator;
        this.router = router;
        this.factory = factory;
        this.contextBuilder = contextBuilder;
        this.usageRecorder = usageRecorder;
        this.tokenUsageCalculator = tokenUsageCalculator;
        this.responseAssembler = responseAssembler;
        this.toolProviders = List.copyOf(toolProviders);
    }

    @Override
    public Flux<AiStreamEvent> stream(AiChatRequest request) {
        return Flux.defer(() -> {
            validator.validate(request);
            String requestId = request.requestId() == null || request.requestId().isBlank() ? UUID.randomUUID().toString() : request.requestId();
            AiModelConfigSnapshot snapshot = router.route(request.options());
            capabilityValidator.validate(request, snapshot);
            AiToolContext toolContext = prepareTools(request, requestId);
            AiCallLog log = usageRecorder.start(requestId, request, snapshot);
            StringBuilder output = new StringBuilder();
            AtomicBoolean terminal = new AtomicBoolean();
            Flux<AiStreamEvent> upstream = factory.get(snapshot).stream(snapshot,
                            contextBuilder.build(request, snapshot), request.options(), toolContext)
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

    private AiToolContext prepareTools(AiChatRequest request, String requestId) {
        if (request.toolCodes().isEmpty()) {
            return null;
        }
        if (toolProviders.isEmpty()) {
            throw new BusinessException(com.example.aichat.common.error.ErrorCode.AI_TOOL_UNAVAILABLE);
        }
        return toolProviders.get(0).prepare(request.userId(), requestId, request.toolCodes());
    }
}
