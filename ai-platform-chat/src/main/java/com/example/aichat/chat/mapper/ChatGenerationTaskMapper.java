package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatGenerationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatGenerationTaskMapper extends BaseMapper<ChatGenerationTask> {
    ChatGenerationTask selectByRequestId(@Param("requestId") String requestId);
}
