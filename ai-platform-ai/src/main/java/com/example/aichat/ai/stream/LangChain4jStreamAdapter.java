package com.example.aichat.ai.stream;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class LangChain4jStreamAdapter {
    private LangChain4jStreamAdapter() {
    }

    public static Flux<AiStreamEvent> adapt(StreamingChatModel model, List<ChatMessage> messages) {
        return Flux.create(sink -> {
            AtomicReference<StreamingHandle> handle = new AtomicReference<>();
            sink.onCancel(() -> {
                StreamingHandle streamingHandle = handle.get();
                if (streamingHandle != null) streamingHandle.cancel();
            });
            model.chat(messages, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (!partialResponse.isEmpty()) sink.next(AiStreamEvent.delta(partialResponse));
                }

                @Override
                public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
                    handle.set(context.streamingHandle());
                    onPartialResponse(partialResponse.text());
                }

                @Override
                public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.next(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
                    sink.complete();
                }
            });
        });
    }
}
