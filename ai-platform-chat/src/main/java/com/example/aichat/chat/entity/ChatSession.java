package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.chat.enums.ChatSessionStatus;

import java.time.LocalDateTime;

@TableName("chat_session")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String title;
    public Long defaultModelId;
    public Integer pinned;
    public ChatSessionStatus status;
    public LocalDateTime lastMessageTime;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
