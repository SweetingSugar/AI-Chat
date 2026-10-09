package com.example.aichat.system.security;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final PasswordEncoder passwordEncoder;

    public PasswordService(PasswordEncoder passwordEncoder) { this.passwordEncoder = passwordEncoder; }

    public String encode(String rawPassword) {
        validate(rawPassword);
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return rawPassword != null && encodedPassword != null && passwordEncoder.matches(rawPassword, encodedPassword);
    }

    public void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 72 || password.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Password must contain 8 to 72 non-blank characters");
        }
    }
}
