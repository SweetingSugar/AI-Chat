package com.example.aichat.ai.stream;

import com.example.aichat.ai.dto.response.AiChatResponse;

public record AiStreamEvent(AiStreamEventType type, String delta, AiChatResponse response, String code,
                            String message) {
    public static AiStreamEvent delta(String text) {
        return new AiStreamEvent(AiStreamEventType.DELTA, text, null, null, null);
    }

    public static AiStreamEvent complete(AiChatResponse response) {
        return new AiStreamEvent(AiStreamEventType.COMPLETE, null, response, null, null);
    }

    public static AiStreamEvent error(String code, String message) {
        return new AiStreamEvent(AiStreamEventType.ERROR, null, null, code, message);
    }
}
