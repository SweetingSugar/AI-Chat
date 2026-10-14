package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
    ChatSession selectOwnedById(@Param("userId") Long userId, @Param("sessionId") Long sessionId);
}
