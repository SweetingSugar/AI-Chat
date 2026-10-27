package com.example.aichat.file.config;

import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class MinioConfiguration {

    @Bean
    public MinioClient minioClient(FileStorageProperties properties) {
        String endpoint = properties.getEndpoint();
        if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
            endpoint = (properties.isSecure() ? "https://" : "http://") + endpoint;
        }
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }
}
