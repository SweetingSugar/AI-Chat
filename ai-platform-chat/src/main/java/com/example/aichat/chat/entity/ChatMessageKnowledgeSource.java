package com.example.aichat.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("chat_message_knowledge_source")
public class ChatMessageKnowledgeSource {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long messageId;
    public Long knowledgeBaseId;
    public Long documentId;
    public Long chunkId;
    public String title;
    public String content;
    public Double score;
    public Integer rankNo;
    public LocalDateTime createTime;
}
