package com.example.aichat.chat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChatMessageQueryDTO(@Min(1) Integer page, @Min(1) @Max(100) Integer size, String status) {
    public ChatMessageQueryDTO {
        page = page == null ? 1 : Math.max(page, 1);
        size = size == null ? 20 : Math.min(Math.max(size, 1), 100);
    }
}
