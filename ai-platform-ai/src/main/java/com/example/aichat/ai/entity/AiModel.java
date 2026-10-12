package com.example.aichat.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("ai_model")
public class AiModel {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long providerId;
    public String modelCode;
    public String modelName;
    public String modelType;
    public Integer contextWindow;
    public Integer maxOutputTokens;
    public BigDecimal temperature;
    public BigDecimal topP;
    public Integer supportStream;
    public Integer isDefault;
    public Integer configVersion;
    public Integer status;
    public Integer sort;
    public String remark;
    public Long createBy;
    public LocalDateTime createTime;
    public Long updateBy;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
