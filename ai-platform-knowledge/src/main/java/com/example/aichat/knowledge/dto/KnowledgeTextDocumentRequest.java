package com.example.aichat.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeTextDocumentRequest(
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 1_000_000) String content) {
}
