package com.example.aichat.file.controller;

import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileDownloadUrlVO;
import com.example.aichat.file.vo.FileMetadataVO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataVO> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(service.upload(file));
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<FileMetadataVO>> uploadBatch(@RequestPart("files") List<MultipartFile> files) {
        return ApiResponse.success(service.uploadBatch(files));
    }

    @GetMapping("/{fileId}")
    public ApiResponse<FileMetadataVO> get(@PathVariable("fileId") Long fileId) {
        return ApiResponse.success(service.findAccessible(fileId));
    }

    @GetMapping("/{fileId}/presigned-url")
    public ApiResponse<FileDownloadUrlVO> presignedUrl(@PathVariable("fileId") Long fileId) {
        return ApiResponse.success(service.createDownloadUrl(fileId));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable("fileId") Long fileId) {
        FileMetadataVO metadata = service.findAccessible(fileId);
        InputStreamResource resource = service.download(fileId);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(metadata.originalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @DeleteMapping("/{fileId}")
    public ApiResponse<Void> delete(@PathVariable("fileId") Long fileId) {
        service.deleteOwned(fileId);
        return ApiResponse.success(null);
    }
}
