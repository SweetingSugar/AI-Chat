package com.example.aichat.ai.service;

import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.ai.entity.AiModel;

import java.util.List;

public interface AiModelService {
    AiModel requireEnabled(Long modelId);

    AiModel requireDefaultEnabled();

    List<AiModelDTO> listAvailableModels();
}
