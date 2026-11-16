package com.example.aichat.ai.provider.adapter;

import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.ai.tool.AiToolDefinition;
import com.example.aichat.ai.tool.AiToolExecutionResult;
import com.example.aichat.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class OpenAiToolCallingLoop {
    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };

    private final StreamingChatModel model;
    private final AiToolContext toolContext;
    private final ObjectMapper objectMapper;
    private final int maxToolRounds;
    private final int maxToolResultBytes;
    private final List<ToolSpecification> specifications;

    public OpenAiToolCallingLoop(StreamingChatModel model, AiToolContext toolContext,
                                 ObjectMapper objectMapper, int maxToolRounds,
                                 int maxToolResultBytes) {
        this.model = model;
        this.toolContext = toolContext;
        this.objectMapper = objectMapper;
        this.maxToolRounds = maxToolRounds;
        this.maxToolResultBytes = maxToolResultBytes;
        this.specifications = toolContext.definitions().stream()
                .map(this::toSpecification)
                .toList();
    }

    public Flux<AiStreamEvent> stream(List<ChatMessage> initialMessages) {
        return Flux.create(sink -> {
            List<ChatMessage> history = new ArrayList<>(initialMessages);
            AtomicReference<StreamingHandle> handle = new AtomicReference<>();
            AtomicReference<Disposable> toolExecution = new AtomicReference<>();
            sink.onCancel(() -> {
                StreamingHandle streamingHandle = handle.get();
                if (streamingHandle != null) {
                    streamingHandle.cancel();
                }
                Disposable disposable = toolExecution.get();
                if (disposable != null) {
                    disposable.dispose();
                }
            });
            runTurn(history, sink, handle, toolExecution, new AtomicInteger());
        });
    }

    private void runTurn(List<ChatMessage> history, FluxSink<AiStreamEvent> sink,
                         AtomicReference<StreamingHandle> handle,
                         AtomicReference<Disposable> toolExecution, AtomicInteger rounds) {
        if (sink.isCancelled()) {
            return;
        }
        ChatRequest request = ChatRequest.builder()
                .messages(List.copyOf(history))
                .toolSpecifications(specifications)
                .build();
        try {
            model.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (partialResponse != null && !partialResponse.isEmpty()) {
                        sink.next(AiStreamEvent.delta(partialResponse));
                    }
                }

                @Override
                public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                    handle.set(context.streamingHandle());
                    onPartialResponse(partialResponse.text());
                }

                @Override
                public void onCompleteResponse(ChatResponse response) {
                    AiMessage assistant = response.aiMessage();
                    if (!assistant.hasToolExecutionRequests()) {
                        sink.complete();
                        return;
                    }
                    if (rounds.get() >= maxToolRounds) {
                        sink.next(AiStreamEvent.error(ErrorCode.AI_TOOL_LOOP_LIMIT.code(),
                                ErrorCode.AI_TOOL_LOOP_LIMIT.message()));
                        sink.complete();
                        return;
                    }
                    if (sink.isCancelled()) {
                        return;
                    }
                    rounds.incrementAndGet();
                    history.add(assistant);
                    Disposable disposable = executeToolCalls(assistant.toolExecutionRequests())
                            .collectList()
                            .subscribe(results -> {
                                if (sink.isCancelled()) {
                                    return;
                                }
                                history.addAll(results);
                                runTurn(history, sink, handle, toolExecution, rounds);
                            }, error -> {
                                if (!sink.isCancelled()) {
                                    sink.next(AiStreamEvent.error(ErrorCode.AI_PROVIDER_ERROR.code(),
                                            ErrorCode.AI_PROVIDER_ERROR.message()));
                                    sink.complete();
                                }
                            });
                    toolExecution.set(disposable);
                    if (sink.isCancelled()) {
                        disposable.dispose();
                    }
                }

                @Override
                public void onError(Throwable error) {
                    sink.next(AiStreamEvent.error(ErrorCode.AI_PROVIDER_ERROR.code(),
                            ErrorCode.AI_PROVIDER_ERROR.message()));
                    sink.complete();
                }
            });
        } catch (RuntimeException error) {
            sink.next(AiStreamEvent.error(ErrorCode.AI_PROVIDER_ERROR.code(),
                    ErrorCode.AI_PROVIDER_ERROR.message()));
            sink.complete();
        }
    }

    private Flux<ToolExecutionResultMessage> executeToolCalls(List<ToolExecutionRequest> requests) {
        return Flux.fromIterable(requests)
                .concatMap(request -> Mono.fromCallable(() -> executeTool(request))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private ToolExecutionResultMessage executeTool(ToolExecutionRequest request) {
        try {
            JsonNode argumentsNode = objectMapper.readTree(request.arguments());
            if (argumentsNode == null || !argumentsNode.isObject()) {
                return errorResult(request, "TOOL_ARGUMENT_INVALID", "Tool arguments must be a JSON object");
            }
            Map<String, Object> arguments = objectMapper.convertValue(argumentsNode, ARGUMENTS_TYPE);
            AiToolExecutionResult result = toolContext.execute(request.name(), arguments);
            if (!result.success()) {
                return errorResult(request, result.errorCode(), result.errorMessage());
            }
            byte[] serialized = objectMapper.writeValueAsBytes(result.data());
            if (serialized.length > maxToolResultBytes) {
                return errorResult(request, ErrorCode.TOOL_EXECUTION_FAILED.code(),
                        "Tool result exceeds configured limit");
            }
            return ToolExecutionResultMessage.builder()
                    .id(request.id())
                    .toolName(request.name())
                    .text(new String(serialized, java.nio.charset.StandardCharsets.UTF_8))
                    .isError(false)
                    .build();
        } catch (JsonProcessingException | IllegalArgumentException error) {
            return errorResult(request, "TOOL_ARGUMENT_INVALID", "Tool arguments are invalid");
        } catch (RuntimeException error) {
            return errorResult(request, ErrorCode.TOOL_EXECUTION_FAILED.code(),
                    ErrorCode.TOOL_EXECUTION_FAILED.message());
        }
    }

    private ToolExecutionResultMessage errorResult(ToolExecutionRequest request, String code, String message) {
        Map<String, Object> result = Map.of(
                "success", false,
                "errorCode", StringUtils.hasText(code) ? code : ErrorCode.TOOL_EXECUTION_FAILED.code(),
                "errorMessage", StringUtils.hasText(message) ? message : ErrorCode.TOOL_EXECUTION_FAILED.message());
        try {
            return ToolExecutionResultMessage.builder()
                    .id(request.id())
                    .toolName(request.name())
                    .text(objectMapper.writeValueAsString(result))
                    .isError(true)
                    .build();
        } catch (JsonProcessingException error) {
            return ToolExecutionResultMessage.from(request, "{\"success\":false,\"errorCode\":\"TOOL_EXECUTION_FAILED\"}");
        }
    }

    private ToolSpecification toSpecification(AiToolDefinition definition) {
        try {
            var specification = objectMapper.createObjectNode();
            specification.put("name", definition.code());
            specification.put("description", definition.description());
            specification.set("parameters", definition.inputSchema().deepCopy());
            return ToolSpecification.fromJson(objectMapper.writeValueAsString(specification));
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Tool specification is invalid", error);
        }
    }
}
