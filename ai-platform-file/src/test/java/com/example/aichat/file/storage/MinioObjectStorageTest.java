package com.example.aichat.file.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MinioObjectStorageTest {

    @Mock
    private MinioClient minioClient;

    @Test
    void uploadsObjectWithContentMetadata() throws Exception {
        MinioObjectStorage storage = new MinioObjectStorage(minioClient);

        storage.put("files", "users/1/a.txt", new ByteArrayInputStream("hello".getBytes()), 5,
                "text/plain");

        verify(minioClient).putObject(any());
    }

    @Test
    void createsPresignedGetUrlWithConfiguredObject() throws Exception {
        when(minioClient.getPresignedObjectUrl(any())).thenReturn("https://download.example/file");
        MinioObjectStorage storage = new MinioObjectStorage(minioClient);

        String url = storage.createPresignedGetUrl("files", "users/1/a.txt", Duration.ofMinutes(10));

        assertThat(url).isEqualTo("https://download.example/file");
        verify(minioClient).getPresignedObjectUrl(any());
    }

}
