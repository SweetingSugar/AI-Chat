package com.example.aichat.chat.converter;

import com.example.aichat.chat.api.ChatHistoryMessage;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.vo.ChatMessageVO;

public final class ChatMessageConverter {
    private ChatMessageConverter() {
    }

    public static ChatMessageVO toVO(ChatMessage message) {
        return new ChatMessageVO(message.id, message.sessionId, message.role, message.content,
                message.sequence, message.status, message.generationTaskId, message.requestId,
                message.modelId, message.errorCode, message.errorMessage, message.createTime,
                message.updateTime);
    }

    public static ChatHistoryMessage toHistory(ChatMessage message) {
        return new ChatHistoryMessage(message.id, message.role, message.content, message.sequence,
                message.status, message.modelId);
    }
}
