package com.example.aichat.chat.application;

import com.example.aichat.ai.dto.response.AiChatResponse;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.stream.AiStreamEvent;
import com.example.aichat.chat.enums.ChatStreamEventType;
import com.example.aichat.chat.gateway.AiChatGateway;
import com.example.aichat.chat.manager.ChatGenerationLockManager;
import com.example.aichat.chat.manager.ChatGenerationTaskManager;
import com.example.aichat.chat.model.ChatGenerationCommand;
import com.example.aichat.chat.model.ChatGenerationResult;
import com.example.aichat.chat.model.ChatMessagePair;
import com.example.aichat.chat.service.ChatGenerationService;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.vo.ChatStreamEventVO;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ChatStreamOrchestrator {
    private final ChatGenerationLockManager lockManager;
    private final ChatGenerationTaskManager taskManager;
    private final ChatGenerationService generationService;
    private final ChatMessageService messageService;
    private final ChatGenerationFinalizer finalizer;
    private final AiChatGateway aiChatGateway;

    public ChatStreamOrchestrator(ChatGenerationLockManager lockManager, ChatGenerationTaskManager taskManager,
                                  ChatGenerationService generationService, ChatMessageService messageService,
                                  ChatGenerationFinalizer finalizer, AiChatGateway aiChatGateway) {
        this.lockManager = lockManager;
        this.taskManager = taskManager;
        this.generationService = generationService;
        this.messageService = messageService;
        this.finalizer = finalizer;
        this.aiChatGateway = aiChatGateway;
    }

    public Flux<ChatStreamEventVO> stream(ChatGenerationCommand command, AiModel model) {
        return Flux.defer(() -> {
            if (!lockManager.tryAcquire(command.sessionId())) {
                return Flux.just(failed(null, command, model, "CHAT_GENERATION_IN_PROGRESS", "A generation is already running for this session"));
            }
            ChatGenerationResult generation;
            try {
                generation = generationService.start(command, model);
            } catch (RuntimeException exception) {
                lockManager.release(command.sessionId());
                return Flux.just(failed(null, command, model, errorCode(exception), safeMessage(exception)));
            }
            taskManager.register(generation);
            StringBuilder output = new StringBuilder();
            AtomicBoolean terminal = new AtomicBoolean(false);
            List<ChatMessagePair> context;
            try {
                context = messageService.context(command.userId(), command.sessionId(),
                        command.existingUserMessageId() == null ? null : findSourceSequence(command));
            } catch (RuntimeException exception) {
                taskManager.remove(generation.requestId());
                lockManager.release(command.sessionId());
                String code = errorCode(exception);
                String message = safeMessage(exception);
                finalizer.fail(generation, output.toString(), code, message);
                return Flux.just(failed(generation, command, model, code, message));
            }
            Flux<ChatStreamEventVO> body = aiChatGateway.stream(command, generation, context)
                    .map(event -> mapEvent(event, command, model, generation, output, terminal))
                    .onErrorResume(exception -> {
                        if (terminal.compareAndSet(false, true)) {
                            String code = errorCode(exception);
                            String message = safeMessage(exception);
                            finalizer.fail(generation, output.toString(), code, message);
                            return Flux.just(failed(generation, command, model, code, message));
                        }
                        return Flux.empty();
                    })
                    .concatWith(Flux.defer(() -> {
                        if (terminal.get()) return Flux.empty();
                        terminal.set(true);
                        finalizer.complete(generation, output.toString(), null);
                        return Flux.just(complete(generation, command, model, null));
                    }));
            return Flux.concat(Flux.just(start(generation, command, model)), body)
                    .doOnCancel(() -> {
                        if (terminal.compareAndSet(false, true)) finalizer.interrupt(generation, output.toString());
                    })
                    .doFinally(signal -> {
                        taskManager.remove(generation.requestId());
                        lockManager.release(command.sessionId());
                    });
        });
    }

    private ChatStreamEventVO mapEvent(AiStreamEvent event, ChatGenerationCommand command, AiModel model,
                                       ChatGenerationResult generation, StringBuilder output, AtomicBoolean terminal) {
        if (event.type() == com.example.aichat.ai.stream.AiStreamEventType.DELTA) {
            String delta = event.delta() == null ? "" : event.delta();
            output.append(delta);
            messageService.append(generation, output.toString());
            return new ChatStreamEventVO(ChatStreamEventType.DELTA, generation.taskId(), command.requestId(), delta,
                    generation.assistantMessageId(), model.id, model.modelCode, null, null, null);
        }
        if (event.type() == com.example.aichat.ai.stream.AiStreamEventType.ERROR) {
            if (terminal.compareAndSet(false, true)) finalizer.fail(generation, output.toString(), event.code(), event.message());
            return failed(generation, command, model, event.code(), event.message());
        }
        if (terminal.compareAndSet(false, true)) finalizer.complete(generation, output.toString(), event.response());
        return complete(generation, command, model, event.response());
    }

    private Long findSourceSequence(ChatGenerationCommand command) {
        return messageService.findOwned(command.existingUserMessageId()).sequence;
    }

    private ChatStreamEventVO start(ChatGenerationResult generation, ChatGenerationCommand command, AiModel model) {
        return new ChatStreamEventVO(ChatStreamEventType.START, generation.taskId(), command.requestId(), null,
                generation.assistantMessageId(), model.id, model.modelCode, null, null, null);
    }

    private ChatStreamEventVO complete(ChatGenerationResult generation, ChatGenerationCommand command,
                                       AiModel model, AiChatResponse response) {
        return new ChatStreamEventVO(ChatStreamEventType.COMPLETE, generation.taskId(), command.requestId(), null,
                generation.assistantMessageId(), model.id, model.modelCode, response == null ? null : response.usage(), null, null);
    }

    private ChatStreamEventVO failed(ChatGenerationResult generation, ChatGenerationCommand command, AiModel model,
                                     String code, String message) {
        return new ChatStreamEventVO(ChatStreamEventType.FAILED, generation == null ? null : generation.taskId(),
                command.requestId(), null, generation == null ? null : generation.assistantMessageId(), model.id,
                model.modelCode, null, code, message);
    }

    private String errorCode(Throwable exception) {
        if (exception instanceof BusinessException businessException) return businessException.errorCode().code();
        return ErrorCode.AI_PROVIDER_ERROR.code();
    }

    private String safeMessage(Throwable exception) {
        if (exception instanceof BusinessException) return exception.getMessage();
        return ErrorCode.AI_PROVIDER_ERROR.message();
    }
}
