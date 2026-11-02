package com.example.aichat.ai.api;

import java.util.List;

public interface AiEmbeddingService {
    List<float[]> embed(Long modelId, List<String> texts);
}
