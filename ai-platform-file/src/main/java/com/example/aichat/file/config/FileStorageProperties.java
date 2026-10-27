package com.example.aichat.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {
    private String type;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucket;
    private boolean secure;
    private long presignExpireSeconds;
    private long maxFileSize;
    private Set<String> allowedContentTypes = new LinkedHashSet<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public boolean isSecure() {
        return secure;
    }

    public void setSecure(boolean secure) {
        this.secure = secure;
    }

    public long getPresignExpireSeconds() {
        return presignExpireSeconds;
    }

    public void setPresignExpireSeconds(long presignExpireSeconds) {
        this.presignExpireSeconds = presignExpireSeconds;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Set<String> getAllowedContentTypes() {
        return allowedContentTypes;
    }

    public void setAllowedContentTypes(Set<String> allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }
}
