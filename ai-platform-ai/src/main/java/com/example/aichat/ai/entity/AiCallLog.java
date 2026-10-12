package com.example.aichat.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.ai.enums.AiCallStatus;

import java.time.LocalDateTime;

@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String requestId;
    public Long userId;
    public Long sessionId;
    public Long messageId;
    public Long providerId;
    public Long modelId;
    public String providerName;
    public String modelCode;
    public Integer inputTokens;
    public Integer outputTokens;
    public Integer totalTokens;
    public String usageSource;
    public Long durationMs;
    public String callType;
    public AiCallStatus status;
    public String finishReason;
    public String errorCode;
    public String errorMessage;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDateTime createTime;
}
