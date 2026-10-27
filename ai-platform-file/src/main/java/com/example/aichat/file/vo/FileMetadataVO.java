package com.example.aichat.file.vo;

import com.example.aichat.file.enums.FileStatus;

import java.time.LocalDateTime;

public record FileMetadataVO(Long id, Long userId, String originalName, String contentType,
                             Long fileSize, FileStatus status, LocalDateTime createTime,
                             LocalDateTime updateTime) {
}
