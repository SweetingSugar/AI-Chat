package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.chat.enums.ChatGenerationStatus;

import java.time.LocalDateTime;

@TableName("chat_generation_task")
public class ChatGenerationTask {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public Long sessionId;
    public Long userMessageId;
    public Long assistantMessageId;
    public Long sourceMessageId;
    public String requestId;
    public Long modelId;
    public ChatGenerationStatus status;
    public String errorCode;
    public String errorMessage;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
}
