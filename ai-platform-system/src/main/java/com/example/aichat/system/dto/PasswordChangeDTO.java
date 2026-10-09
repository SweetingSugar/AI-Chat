package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeDTO(@NotBlank @Size(max = 72) String oldPassword,
                                @NotBlank @Size(min = 8, max = 72) String newPassword) { }
