package com.example.aichat.chat.vo;

import com.example.aichat.chat.enums.ChatMessageRole;
import com.example.aichat.chat.enums.ChatMessageStatus;
import com.example.aichat.file.vo.FileMetadataVO;

import java.time.LocalDateTime;
import java.util.List;

public record ChatMessageVO(Long id, Long sessionId, ChatMessageRole role, String content, Long sequence,
                            ChatMessageStatus status, Long generationTaskId, String requestId, Long modelId,
                            String errorCode, String errorMessage, LocalDateTime createTime, LocalDateTime updateTime,
                            List<FileMetadataVO> files) {
    public ChatMessageVO {
        files = files == null ? List.of() : List.copyOf(files);
    }
}
