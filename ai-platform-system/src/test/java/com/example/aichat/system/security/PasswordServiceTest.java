package com.example.aichat.system.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class PasswordServiceTest {
    private final PasswordService passwordService = new PasswordService(new BCryptPasswordEncoder());

    @Test
    void hashesAndMatchesPasswordsWithoutReturningPlaintext() {
        String hash = passwordService.encode("safe-password-1");

        assertThat(hash).isNotEqualTo("safe-password-1");
        assertThat(passwordService.matches("safe-password-1", hash)).isTrue();
        assertThat(passwordService.matches("wrong-password", hash)).isFalse();
    }

    @Test
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> passwordService.encode("short"))
                .isInstanceOf(BusinessException.class);
    }
}
