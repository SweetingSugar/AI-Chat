package com.example.aichat.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.aichat.file.enums.FileStatus;

import java.time.LocalDateTime;

@TableName("file_metadata")
public class FileMetadata {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long userId;
    public String bucketName;
    public String objectKey;
    public String originalName;
    public String contentType;
    public Long fileSize;
    public String fileHash;
    public FileStatus status;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
    @TableLogic
    public Integer deleted;
}
