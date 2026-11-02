package com.example.aichat.chat.vo;

import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;
import com.example.aichat.file.vo.FileMetadataVO;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageVO(Long id, Long sessionId, ChatMessageRole role, String content, Long sequence,
                            ChatMessageStatus status, Long generationTaskId, String requestId, Long modelId,
                            String errorCode, String errorMessage, LocalDateTime createTime, LocalDateTime updateTime,
                            List<FileMetadataVO> files, List<ChatKnowledgeSourceVO> sources) {
    public ChatMessageVO(Long id, Long sessionId, ChatMessageRole role, String content, Long sequence,
                         ChatMessageStatus status, Long generationTaskId, String requestId, Long modelId,
                         String errorCode, String errorMessage, LocalDateTime createTime, LocalDateTime updateTime,
                         List<FileMetadataVO> files) {
        this(id, sessionId, role, content, sequence, status, generationTaskId, requestId, modelId,
                errorCode, errorMessage, createTime, updateTime, files, List.of());
    }

    public ChatMessageVO {
        files = files == null ? List.of() : List.copyOf(files);
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
