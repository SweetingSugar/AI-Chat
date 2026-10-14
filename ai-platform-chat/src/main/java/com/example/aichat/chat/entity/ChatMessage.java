package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;

import java.time.LocalDateTime;

@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sessionId;
    public Long userId;
    public ChatMessageRole role;
    public String content;
    public Long sequence;
    public ChatMessageStatus status;
    public Long generationTaskId;
    public String requestId;
    public Long modelId;
    public String errorCode;
    public String errorMessage;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
