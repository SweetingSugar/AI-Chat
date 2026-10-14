package com.example.aichat.chat.dto;

import jakarta.validation.constraints.Size;

public record CreateSessionDTO(@Size(max = 200) String title, Long defaultModelId) {
}
