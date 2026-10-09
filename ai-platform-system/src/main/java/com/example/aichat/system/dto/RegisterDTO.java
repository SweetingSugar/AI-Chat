package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterDTO(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,50}$", message = "username must be 3-50 letters, numbers, or underscores") String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Size(max = 50) String nickname,
        @Size(max = 100) String email,
        @Size(max = 20) String phone) { }
