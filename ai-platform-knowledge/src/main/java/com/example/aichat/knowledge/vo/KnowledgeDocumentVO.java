package com.example.aichat.knowledge.vo;

import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;

import java.time.LocalDateTime;

public record KnowledgeDocumentVO(Long id, String title, KnowledgeDocumentSourceType sourceType,
                                  Long fileId, KnowledgeDocumentStatus status, Long indexVersion,
                                  Long activeIndexVersion, Integer chunkCount, String errorCode,
                                  String errorMessage, LocalDateTime createTime,
                                  LocalDateTime updateTime) {
}
