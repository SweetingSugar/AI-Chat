package com.example.aichat.ai.config;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCredentialCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsWithRandomNonceAndDecrypts() {
        AiCredentialCipher cipher = new AiCredentialCipher(KEY);

        String first = cipher.encrypt("sk-secret");
        String second = cipher.encrypt("sk-secret");

        assertThat(first).startsWith("v1:").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-secret");
        assertThat(cipher.decrypt(second)).isEqualTo("sk-secret");
    }

    @Test
    void rejectsMissingKeyAndTamperedCiphertext() {
        assertThatThrownBy(() -> new AiCredentialCipher("").encrypt("sk-secret"))
                .isInstanceOf(IllegalStateException.class);

        AiCredentialCipher cipher = new AiCredentialCipher(KEY);
        assertThatThrownBy(() -> cipher.decrypt("v1:not-valid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
