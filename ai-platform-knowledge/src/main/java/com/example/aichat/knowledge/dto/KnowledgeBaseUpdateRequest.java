package com.example.aichat.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeBaseUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 1000) String description) {
}
