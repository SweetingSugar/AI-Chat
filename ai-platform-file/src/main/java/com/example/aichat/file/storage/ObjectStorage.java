package com.example.aichat.file.storage;

import java.io.InputStream;
import java.time.Duration;

public interface ObjectStorage {
    void put(String bucketName, String objectKey, InputStream inputStream,
             long contentLength, String contentType);

    InputStream get(String bucketName, String objectKey);

    void delete(String bucketName, String objectKey);

    String createPresignedGetUrl(String bucketName, String objectKey, Duration expire);
}
