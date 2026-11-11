package com.example.aichat.tool.validation;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.tool.enums.ToolAuthType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCredentialHeadersTest {
    private final ToolCredentialHeaders headers = new ToolCredentialHeaders(
            new AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            new ObjectMapper());

    @Test
    void encryptsAndResolvesAuthenticationAndSecretHeaders() {
        String credentialCiphertext = headers.encryptCredential("token-123");
        String secretHeadersCiphertext = headers.encryptSecretHeaders(Map.of("X-Internal", "secret-value"));

        Map<String, String> result = headers.resolve(
                ToolAuthType.BEARER,
                null,
                credentialCiphertext,
                secretHeadersCiphertext);

        assertThat(result).containsEntry("Authorization", "Bearer token-123")
                .containsEntry("X-Internal", "secret-value");
        assertThat(credentialCiphertext).doesNotContain("token-123");
        assertThat(secretHeadersCiphertext).doesNotContain("secret-value");
    }

    @Test
    void usesConfiguredHeaderForApiKey() {
        String credentialCiphertext = headers.encryptCredential("api-key");

        assertThat(headers.resolve(ToolAuthType.API_KEY, "X-Api-Key", credentialCiphertext, null))
                .containsEntry("X-Api-Key", "api-key");
    }
}
