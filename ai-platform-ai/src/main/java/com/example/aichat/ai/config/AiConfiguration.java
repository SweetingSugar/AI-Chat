package com.example.aichat.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfiguration {
    @Bean
    public AiCredentialCipher aiCredentialCipher(AiProperties properties) {
        return new AiCredentialCipher(properties.getCredentialEncryptionKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public AiCredentialResolver aiCredentialResolver(AiCredentialCipher credentialCipher) {
        return credentialCipher::decrypt;
    }
}
