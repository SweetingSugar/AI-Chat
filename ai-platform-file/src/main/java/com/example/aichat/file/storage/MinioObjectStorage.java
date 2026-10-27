package com.example.aichat.file.storage;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.file.config.FileStorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient minioClient;

    public MinioObjectStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @Override
    public void put(String bucketName, String objectKey, InputStream inputStream,
                    long contentLength, String contentType) {
        try {
            ensureBucket(bucketName);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .stream(inputStream, contentLength, -1)
                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to upload file");
        }
    }

    @Override
    public InputStream get(String bucketName, String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to download file");
        }
    }

    @Override
    public void delete(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectKey)
                    .build());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.FILE_DELETE_FAILED, "Unable to delete file");
        }
    }

    @Override
    public String createPresignedGetUrl(String bucketName, String objectKey, Duration expire) {
        try {
            int seconds = Math.toIntExact(expire.getSeconds());
            if (seconds < 1) {
                throw new IllegalArgumentException("Presigned URL expiration must be positive");
            }
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(objectKey)
                    .expiry(seconds, TimeUnit.SECONDS)
                    .build());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.FILE_STORAGE_ERROR, "Unable to create download URL");
        }
    }

    private void ensureBucket(String bucketName) throws Exception {
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }
}
