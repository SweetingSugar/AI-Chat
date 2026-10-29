package com.example.aichat.file.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.vo.FileDownloadUrlVO;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService extends FileAccessService, FileContentService {
    FileMetadataVO upload(MultipartFile file);

    List<FileMetadataVO> uploadBatch(List<MultipartFile> files);

    FileMetadataVO findAccessible(Long fileId);

    InputStreamResource download(Long fileId);

    FileDownloadUrlVO createDownloadUrl(Long fileId);

    FileDownloadUrlVO createAdminDownloadUrl(Long fileId);

    void deleteOwned(Long fileId);

    PageResult<FileMetadataVO> page(FileQueryRequest request);

    FileMetadataVO findByAdmin(Long fileId);

    void deleteByAdmin(Long fileId);
}
