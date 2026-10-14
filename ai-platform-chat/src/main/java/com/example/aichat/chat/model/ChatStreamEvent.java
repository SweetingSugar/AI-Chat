package com.example.aichat.chat.model;

import com.example.aichat.ai.dto.response.AiChatResponse;
import com.example.aichat.ai.stream.AiStreamEvent;

public record ChatStreamEvent(AiStreamEvent source) {
    public boolean isDelta() {
        return source.type() == com.example.aichat.ai.stream.AiStreamEventType.DELTA;
    }

    public boolean isComplete() {
        return source.type() == com.example.aichat.ai.stream.AiStreamEventType.COMPLETE;
    }

    public boolean isError() {
        return source.type() == com.example.aichat.ai.stream.AiStreamEventType.ERROR;
    }

    public String delta() {
        return source.delta();
    }

    public AiChatResponse response() {
        return source.response();
    }

    public String code() {
        return source.code();
    }

    public String message() {
        return source.message();
    }
}
