package com.example.aichat.file.service;

import com.example.aichat.file.content.FileContent;

import java.util.List;

public interface FileContentService {
    List<FileContent> readOwnedActive(Long userId, List<Long> fileIds);
}
