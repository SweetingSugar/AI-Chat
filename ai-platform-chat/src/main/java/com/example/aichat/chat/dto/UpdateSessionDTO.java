package com.example.aichat.chat.dto;

import jakarta.validation.constraints.Size;

public record UpdateSessionDTO(@Size(max = 200) String title, Long defaultModelId) {
}
