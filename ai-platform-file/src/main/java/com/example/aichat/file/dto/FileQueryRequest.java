package com.example.aichat.file.dto;

import com.example.aichat.file.enums.FileStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class FileQueryRequest {
    private Integer page = 1;
    private Integer size = 20;
    private Long userId;
    private String originalName;
    private String contentType;
    private FileStatus status;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createdFrom;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createdTo;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public FileStatus getStatus() {
        return status;
    }

    public void setStatus(FileStatus status) {
        this.status = status;
    }

    public LocalDate getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(LocalDate createdFrom) {
        this.createdFrom = createdFrom;
    }

    public LocalDate getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(LocalDate createdTo) {
        this.createdTo = createdTo;
    }
}
