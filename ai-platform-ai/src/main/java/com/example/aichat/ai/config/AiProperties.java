package com.example.aichat.ai.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "ai")
@Validated
public class AiProperties {
    private int executorCorePoolSize = 4;
    private int executorMaxPoolSize = 16;
    private int executorQueueCapacity = 200;
    @Min(1)
    private int maxToolRounds = 8;
    @Min(1)
    private int maxToolResultBytes = 65_536;
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

    public int getMaxToolRounds() {
        return maxToolRounds;
    }

    public void setMaxToolRounds(int value) {
        maxToolRounds = value;
    }

    public int getMaxToolResultBytes() {
        return maxToolResultBytes;
    }

    public void setMaxToolResultBytes(int value) {
        maxToolResultBytes = value;
    }

    public String getCredentialEncryptionKey() {
        return credentialEncryptionKey;
    }

    public void setCredentialEncryptionKey(String credentialEncryptionKey) {
        this.credentialEncryptionKey = credentialEncryptionKey;
    }
}
