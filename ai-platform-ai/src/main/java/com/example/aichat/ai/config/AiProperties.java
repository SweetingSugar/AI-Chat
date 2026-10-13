package com.example.aichat.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private int executorCorePoolSize = 4;
    private int executorMaxPoolSize = 16;
    private int executorQueueCapacity = 200;
    private String credentialEncryptionKey;

    public int getExecutorCorePoolSize() {
        return executorCorePoolSize;
    }

    public void setExecutorCorePoolSize(int value) {
        executorCorePoolSize = value;
    }

    public int getExecutorMaxPoolSize() {
        return executorMaxPoolSize;
    }

    public void setExecutorMaxPoolSize(int value) {
        executorMaxPoolSize = value;
    }

    public int getExecutorQueueCapacity() {
        return executorQueueCapacity;
    }

    public void setExecutorQueueCapacity(int value) {
        executorQueueCapacity = value;
    }

    public String getCredentialEncryptionKey() {
        return credentialEncryptionKey;
    }

    public void setCredentialEncryptionKey(String credentialEncryptionKey) {
        this.credentialEncryptionKey = credentialEncryptionKey;
    }
}
