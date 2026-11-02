package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatMessageKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageKnowledgeBaseMapper extends BaseMapper<ChatMessageKnowledgeBase> {
    List<Long> selectKnowledgeBaseIds(@Param("messageId") Long messageId);
}
