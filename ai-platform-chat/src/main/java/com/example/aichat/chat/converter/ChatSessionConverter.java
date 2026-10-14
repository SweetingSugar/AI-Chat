package com.example.aichat.chat.converter;

import com.example.aichat.chat.entity.ChatSession;
import com.example.aichat.chat.vo.ChatSessionVO;

public final class ChatSessionConverter {
    private ChatSessionConverter() {
    }

    public static ChatSessionVO toVO(ChatSession session) {
        return new ChatSessionVO(session.id, session.title, session.defaultModelId,
                Integer.valueOf(1).equals(session.pinned), session.status,
                session.lastMessageTime, session.createTime, session.updateTime);
    }
}
