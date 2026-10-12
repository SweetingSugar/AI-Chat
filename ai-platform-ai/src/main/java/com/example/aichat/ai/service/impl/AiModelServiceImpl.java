package com.example.aichat.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.exception.AiModelNotFoundException;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.ai.service.AiProviderService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiModelServiceImpl implements AiModelService, AiModelQueryService {
    private final AiModelMapper modelMapper;
    private final AiProviderService providerService;

    public AiModelServiceImpl(AiModelMapper modelMapper, AiProviderService providerService) {
        this.modelMapper = modelMapper;
        this.providerService = providerService;
    }

    @Override
    public AiModel requireEnabled(Long modelId) {
        AiModel model = modelMapper.selectById(modelId);
        if (model == null || !Integer.valueOf(1).equals(model.status)) throw new AiModelNotFoundException(modelId);
        providerService.requireEnabled(model.providerId);
        return model;
    }

    @Override
    public AiModel requireDefaultEnabled() {
        AiModel model = modelMapper.selectOne(Wrappers.<AiModel>query()
                .eq("is_default", 1).eq("status", 1).orderByAsc("sort"));
        if (model == null) throw new AiModelNotFoundException(null);
        providerService.requireEnabled(model.providerId);
        return model;
    }

    @Override
    public List<AiModelDTO> listAvailableModels() {
        return modelMapper.selectList(Wrappers.<AiModel>query().eq("status", 1).orderByAsc("sort"))
                .stream().map(this::toDto).toList();
    }

    private AiModelDTO toDto(AiModel model) {
        AiProvider provider = providerService.requireEnabled(model.providerId);
        return new AiModelDTO(model.id, model.providerId, provider.providerName, model.modelCode, model.modelName,
                model.contextWindow, model.maxOutputTokens, Integer.valueOf(1).equals(model.supportStream),
                Integer.valueOf(1).equals(model.isDefault));
    }
}
