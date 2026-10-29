package com.example.aichat.file.content;

public record FileContent(Long fileId, String fileName, String contentType,
                          String text, byte[] bytes) {
    public FileContent {
        bytes = bytes == null ? null : bytes.clone();
    }
}
