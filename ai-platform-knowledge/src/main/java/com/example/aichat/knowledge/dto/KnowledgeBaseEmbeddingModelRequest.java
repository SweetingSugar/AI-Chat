package com.example.aichat.knowledge.dto;

import jakarta.validation.constraints.NotNull;

public record KnowledgeBaseEmbeddingModelRequest(@NotNull Long embeddingModelId) {
}
