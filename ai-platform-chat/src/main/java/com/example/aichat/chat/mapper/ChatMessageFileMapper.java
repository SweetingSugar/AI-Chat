package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatMessageFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMessageFileMapper extends BaseMapper<ChatMessageFile> {
    List<ChatMessageFile> selectByMessageIds(@Param("messageIds") List<Long> messageIds);
}
