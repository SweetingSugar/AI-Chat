package com.example.aichat.file.service;

import com.example.aichat.file.vo.FileMetadataVO;

import java.util.List;

public interface FileAccessService {
    List<FileMetadataVO> validateOwnedActiveFiles(Long userId, List<Long> fileIds);

    List<FileMetadataVO> findByFileIds(Long userId, List<Long> fileIds);
}
