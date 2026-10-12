package com.example.aichat.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public AiCredentialResolver aiCredentialResolver() {
        return ciphertext -> {
            throw new IllegalStateException("No AI credential resolver is configured");
        };
    }
}
