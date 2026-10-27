package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("chat_message_file")
public class ChatMessageFile {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long messageId;
    public Long fileId;
    public Integer sortOrder;
    public LocalDateTime createTime;
}
