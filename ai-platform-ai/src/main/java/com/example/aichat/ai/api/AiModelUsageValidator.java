package com.example.aichat.ai.api;

public interface AiModelUsageValidator {
    void assertCanDelete(Long modelId);

    void assertCanChangeEmbeddingDefinition(Long modelId, Integer newDimension);
}
