package com.example.aichat.ai.stream;

import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.PartialResponse;
import reactor.core.publisher.Flux;

import java.util.concurrent.Flow;

public final class LangChain4jStreamAdapter {
    private LangChain4jStreamAdapter() {
    }

    public static Flux<AiStreamEvent> adapt(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        return Flux.create(sink -> publisher.subscribe(new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription value) {
                subscription = value;
                sink.onCancel(value::cancel);
                value.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                if (event instanceof PartialResponse response && !response.text().isEmpty())
                    sink.next(AiStreamEvent.delta(response.text()));
            }

            @Override
            public void onError(Throwable error) {
                sink.next(AiStreamEvent.error("AI_PROVIDER_ERROR", "AI provider request failed"));
                sink.complete();
            }

            @Override
            public void onComplete() {
                sink.complete();
            }
        }));
    }
}
