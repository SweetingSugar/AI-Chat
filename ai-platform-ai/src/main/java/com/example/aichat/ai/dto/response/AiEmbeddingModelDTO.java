package com.example.aichat.ai.dto.response;

public record AiEmbeddingModelDTO(Long id, Long providerId, String providerName, String modelCode,
                                  String modelName, Integer embeddingDimension) { }
