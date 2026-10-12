package com.example.aichat.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("ai_prompt_template")
public class AiPromptTemplate {
    @TableId(type = IdType.AUTO)
    public Long id;
    public String promptCode;
    public String promptName;
    public String promptContent;
    public String description;
    public String variables;
    public Integer version;
    public Integer isDefault;
    public Integer status;
    public Integer sort;
    public Long createBy;
    public LocalDateTime createTime;
    public Long updateBy;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
