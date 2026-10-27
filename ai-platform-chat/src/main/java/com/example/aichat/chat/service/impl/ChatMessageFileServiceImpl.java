package com.example.aichat.chat.service.impl;

import com.example.aichat.chat.entity.ChatMessageFile;
import com.example.aichat.chat.mapper.ChatMessageFileMapper;
import com.example.aichat.chat.service.ChatMessageFileService;
import com.example.aichat.file.service.FileAccessService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ChatMessageFileServiceImpl implements ChatMessageFileService {
    private final ChatMessageFileMapper mapper;
    private final FileAccessService fileAccessService;

    public ChatMessageFileServiceImpl(ChatMessageFileMapper mapper, FileAccessService fileAccessService) {
        this.mapper = mapper;
        this.fileAccessService = fileAccessService;
    }

    @Override
    @Transactional
    public void bind(Long messageId, Long userId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) return;
        List<FileMetadataVO> files = fileAccessService.validateOwnedActiveFiles(userId, fileIds);
        for (int index = 0; index < files.size(); index++) {
            ChatMessageFile relation = new ChatMessageFile();
            relation.messageId = messageId;
            relation.fileId = files.get(index).id();
            relation.sortOrder = index;
            mapper.insert(relation);
        }
    }

    @Override
    public Map<Long, List<FileMetadataVO>> findByMessageIds(Long userId, List<Long> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return Map.of();
        Map<Long, List<FileMetadataVO>> result = new LinkedHashMap<>();
        messageIds.forEach(messageId -> result.put(messageId, new ArrayList<>()));
        List<ChatMessageFile> relations = mapper.selectByMessageIds(messageIds);
        if (relations.isEmpty()) return result;
        List<Long> fileIds = relations.stream().map(relation -> relation.fileId).distinct().toList();
        Map<Long, FileMetadataVO> files = new HashMap<>();
        fileAccessService.findByFileIds(userId, fileIds).forEach(file -> files.put(file.id(), file));
        for (ChatMessageFile relation : relations) {
            FileMetadataVO file = files.get(relation.fileId);
            if (file != null) result.get(relation.messageId).add(file);
        }
        return result;
    }
}
