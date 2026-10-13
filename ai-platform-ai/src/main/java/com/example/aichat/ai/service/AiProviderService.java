package com.example.aichat.ai.service;

import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.dto.response.AiProviderManagementDTO;
import com.example.aichat.ai.entity.AiProvider;

import java.util.List;

public interface AiProviderService {
    List<AiProviderManagementDTO> listManagement();

    AiProviderManagementDTO getManagementRequired(Long providerId);

    AiProvider getRequired(Long providerId);

    AiProviderManagementDTO create(AiProviderUpsertRequest request);

    AiProviderManagementDTO update(Long providerId, AiProviderUpsertRequest request);

    void delete(Long providerId);

    AiProvider requireEnabled(Long providerId);
}
