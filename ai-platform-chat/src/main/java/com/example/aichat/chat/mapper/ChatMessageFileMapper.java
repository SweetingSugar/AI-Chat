package com.example.aichat.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.aichat.chat.entity.ChatMessageFile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageFileMapper extends BaseMapper<ChatMessageFile> {
    @Select({
            "<script>",
            "select id, message_id, file_id, sort_order, create_time",
            "from chat_message_file",
            "where message_id in",
            "<foreach collection='messageIds' item='messageId' open='(' separator=',' close=')'>",
            "#{messageId}",
            "</foreach>",
            "order by message_id, sort_order, id",
            "</script>"
    })
    List<ChatMessageFile> selectByMessageIds(@Param("messageIds") List<Long> messageIds);
}
