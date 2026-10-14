package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatMessage;
import com.example.aichat.chat.enums.ChatMessageRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
    List<ChatMessage> selectHistory(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                                    @Param("beforeSequence") Long beforeSequence);

    Long selectMaxSequence(@Param("userId") Long userId, @Param("sessionId") Long sessionId);

    ChatMessage selectOwnedById(@Param("userId") Long userId, @Param("messageId") Long messageId);

    ChatMessage selectPreviousByRole(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                                     @Param("sequence") Long sequence, @Param("role") ChatMessageRole role);
}
