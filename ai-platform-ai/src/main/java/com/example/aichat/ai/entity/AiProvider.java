package com.example.aichat.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.ai.enums.AiProviderType;

import java.time.LocalDateTime;

@TableName("ai_provider")
public class AiProvider {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String providerCode;
    public String providerName;
    public AiProviderType providerType;
    public String baseUrl;
    public String apiKeyCiphertext;
    public Integer credentialVersion;
    public Integer connectTimeout;
    public Integer readTimeout;
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
