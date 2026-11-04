package com.example.aichat.ai.api;

import com.example.aichat.ai.dto.response.AiEmbeddingModelDTO;
import com.example.aichat.ai.dto.response.AiModelDTO;

import java.util.List;

public interface AiModelQueryService {
    List<AiModelDTO> listAvailableModels();

    List<AiEmbeddingModelDTO> listAvailableEmbeddingModels();
}
