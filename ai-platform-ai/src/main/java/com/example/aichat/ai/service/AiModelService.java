package com.example.aichat.ai.service;

import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.ai.dto.request.AiModelUpsertRequest;
import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.entity.AiModel;

import java.util.List;

public interface AiModelService {
    List<AiModelManagementDTO> listManagement();

    AiModelManagementDTO getManagementRequired(Long modelId);

    AiModelManagementDTO create(AiModelUpsertRequest request);

    AiModelManagementDTO update(Long modelId, AiModelUpsertRequest request);

    void delete(Long modelId);

    AiModel requireEnabled(Long modelId);

    AiModel requireDefaultEnabled();

    List<AiModelDTO> listAvailableModels();
}
