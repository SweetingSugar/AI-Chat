package com.example.aichat.chat.service;

import com.example.aichat.file.vo.FileMetadataVO;

import java.util.List;
import java.util.Map;

public interface ChatMessageFileService {
    void bind(Long messageId, Long userId, List<Long> fileIds);

    Map<Long, List<Long>> findFileIdsByMessageIds(List<Long> messageIds);

    Map<Long, List<FileMetadataVO>> findByMessageIds(Long userId, List<Long> messageIds);
}
