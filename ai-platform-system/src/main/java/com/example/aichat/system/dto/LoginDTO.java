package com.example.aichat.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(@NotBlank @Size(max = 50) String username,
                       @NotBlank @Size(max = 72) String password) { }
