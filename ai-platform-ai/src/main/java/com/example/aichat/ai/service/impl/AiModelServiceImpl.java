package com.example.aichat.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.api.AiModelUsageValidator;
import com.example.aichat.ai.dto.request.AiModelUpsertRequest;
import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiModelType;
import com.example.aichat.ai.exception.AiModelNotFoundException;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

@Service
public class AiModelServiceImpl implements AiModelService, AiModelQueryService {
    private final AiModelMapper modelMapper;
    private final AiProviderService providerService;
    private final List<AiModelUsageValidator> usageValidators;

    public AiModelServiceImpl(AiModelMapper modelMapper, AiProviderService providerService,
                              List<AiModelUsageValidator> usageValidators) {
        this.modelMapper = modelMapper;
        this.providerService = providerService;
        this.usageValidators = usageValidators;
    }

    @Override
    public List<AiModelManagementDTO> listManagement() {
        return modelMapper.selectList(Wrappers.<AiModel>lambdaQuery().orderByAsc(AiModel::getSort, AiModel::getId))
                .stream().map(this::toManagementDto).toList();
    }

    @Override
    public AiModelManagementDTO getManagementRequired(Long modelId) {
        return toManagementDto(getRequired(modelId));
    }

    @Override
    @Transactional
    public AiModelManagementDTO create(AiModelUpsertRequest request) {
        AiProvider provider = requireModelProvider(request.providerId(), request.status());
        assertCodeAvailable(request.providerId(), request.modelCode(), null);
        AiModel model = copy(request, new AiModel());
        model.setConfigVersion(1);
        if (Integer.valueOf(1).equals(model.getIsDefault())) clearOtherDefaults(null);
        modelMapper.insert(model);
        return toManagementDto(model, provider);
    }

    @Override
    @Transactional
    public AiModelManagementDTO update(Long modelId, AiModelUpsertRequest request) {
        AiModel model = getRequired(modelId);
        AiModelType requestedType = validateModelDefinition(request);
        if (embeddingDefinitionChanged(model, requestedType, request.embeddingDimension())) {
            usageValidators.forEach(validator ->
                    validator.assertCanChangeEmbeddingDefinition(modelId, request.embeddingDimension()));
        }
        AiProvider provider = requireModelProvider(request.providerId(), request.status());
        assertCodeAvailable(request.providerId(), request.modelCode(), modelId);
        copy(request, model);
        model.setConfigVersion(model.getConfigVersion() == null ? 1 : model.getConfigVersion() + 1);
        if (Integer.valueOf(1).equals(model.getIsDefault())) clearOtherDefaults(modelId);
        modelMapper.updateById(model);
        return toManagementDto(model, provider);
    }

    @Override
    @Transactional
    public void delete(Long modelId) {
        getRequired(modelId);
        usageValidators.forEach(validator -> validator.assertCanDelete(modelId));
        modelMapper.deleteById(modelId);
    }

    @Override
    public AiModel requireEnabled(Long modelId) {
        AiModel model = getRequired(modelId);
        if (!Integer.valueOf(1).equals(model.status)) throw new AiModelNotFoundException(modelId);
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
        return modelMapper.selectList(Wrappers.<AiModel>query().eq("status", 1)
                        .eq("model_type", AiModelType.CHAT.name()).orderByAsc("sort"))
                .stream()
                .filter(model -> AiModelType.CHAT.name().equalsIgnoreCase(model.modelType))
                .flatMap(model -> availableDto(model).stream()).toList();
    }

    @Override
    public AiModel requireEnabledEmbedding(Long modelId) {
        AiModel model = getRequired(modelId);
        if (!Integer.valueOf(1).equals(model.status)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE,
                    "Embedding model is disabled");
        }
        if (parseType(model.modelType) != AiModelType.EMBEDDING) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_MODEL_INVALID,
                    "Selected model is not an embedding model");
        }
        if (model.embeddingDimension == null || model.embeddingDimension < 1) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_MODEL_INVALID,
                    "Embedding model dimension is invalid");
        }
        providerService.requireEnabled(model.providerId);
        return model;
    }

    private AiModel getRequired(Long modelId) {
        AiModel model = modelMapper.selectById(modelId);
        if (model == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI model not found");
        return model;
    }

    private AiProvider requireModelProvider(Long providerId, Integer modelStatus) {
        AiProvider provider = providerService.getRequired(providerId);
        if (Integer.valueOf(1).equals(modelStatus == null ? 1 : modelStatus)
                && !Integer.valueOf(1).equals(provider.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Enabled model requires an enabled provider");
        }
        return provider;
    }

    private void assertCodeAvailable(Long providerId, String modelCode, Long excludedId) {
        var query = Wrappers.<AiModel>lambdaQuery().eq(AiModel::getProviderId, providerId).eq(AiModel::getModelCode, modelCode);
        if (excludedId != null) query.ne(AiModel::getId, excludedId);
        if (modelMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "AI model code already exists for provider");
        }
    }

    private void clearOtherDefaults(Long currentModelId) {
        var query = Wrappers.<AiModel>lambdaUpdate().eq(AiModel::getIsDefault, 1).set(AiModel::getIsDefault, 0);
        if (currentModelId != null) query.ne(AiModel::getId, currentModelId);
        modelMapper.update(null, query);
    }

    private AiModel copy(AiModelUpsertRequest request, AiModel model) {
        AiModelType type = validateModelDefinition(request);
        model.setProviderId(request.providerId());
        model.setModelCode(request.modelCode().trim());
        model.setModelName(request.modelName().trim());
        model.setModelType(type.name());
        model.setEmbeddingDimension(request.embeddingDimension());
        model.setContextWindow(request.contextWindow() == null ? 8192 : request.contextWindow());
        model.setMaxOutputTokens(request.maxOutputTokens() == null ? 1024 : request.maxOutputTokens());
        model.setTemperature(request.temperature());
        model.setTopP(request.topP());
        model.setSupportStream(request.supportStream() == null ? 1 : request.supportStream());
        model.setSupportVision(request.supportVision() == null ? 0 : request.supportVision());
        model.setIsDefault(request.isDefault() == null ? 0 : request.isDefault());
        model.setStatus(request.status() == null ? 1 : request.status());
        model.setSort(request.sort() == null ? 0 : request.sort());
        model.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        return model;
    }

    private AiModelType validateModelDefinition(AiModelUpsertRequest request) {
        AiModelType type = parseType(request.modelType());
        if (type == AiModelType.EMBEDDING
                && (request.embeddingDimension() == null || request.embeddingDimension() < 1)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Embedding model requires a positive dimension");
        }
        if (type == AiModelType.CHAT && request.embeddingDimension() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Chat model cannot define an embedding dimension");
        }
        return type;
    }

    private AiModelType parseType(String modelType) {
        try {
            return AiModelType.valueOf(modelType.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unknown AI model type");
        }
    }

    private boolean embeddingDefinitionChanged(AiModel model, AiModelType requestedType,
                                               Integer requestedDimension) {
        AiModelType currentType = parseType(model.modelType);
        if (currentType != AiModelType.EMBEDDING && requestedType != AiModelType.EMBEDDING) {
            return false;
        }
        return currentType != requestedType
                || !java.util.Objects.equals(model.embeddingDimension, requestedDimension);
    }

    private java.util.Optional<AiModelDTO> availableDto(AiModel model) {
        try {
            AiProvider provider = providerService.getRequired(model.providerId);
            return Integer.valueOf(1).equals(provider.getStatus()) ? java.util.Optional.of(toDto(model, provider)) : java.util.Optional.empty();
        } catch (BusinessException ignored) {
            return java.util.Optional.empty();
        }
    }

    private AiModelManagementDTO toManagementDto(AiModel model) {
        return toManagementDto(model, providerService.getRequired(model.getProviderId()));
    }

    private AiModelManagementDTO toManagementDto(AiModel model, AiProvider provider) {
        return new AiModelManagementDTO(model.getId(), model.getProviderId(), provider.getProviderName(), model.getModelCode(),
                model.getModelName(), model.getModelType(), model.getEmbeddingDimension(), model.getContextWindow(), model.getMaxOutputTokens(), model.getTemperature(),
                model.getTopP(), Integer.valueOf(1).equals(model.getSupportStream()),
                Integer.valueOf(1).equals(model.getSupportVision()),
                Integer.valueOf(1).equals(model.getIsDefault()),
                model.getConfigVersion(), model.getStatus(), model.getSort(), model.getRemark());
    }

    private AiModelDTO toDto(AiModel model) {
        AiProvider provider = providerService.requireEnabled(model.providerId);
        return toDto(model, provider);
    }

    private AiModelDTO toDto(AiModel model, AiProvider provider) {
        return new AiModelDTO(model.id, model.providerId, provider.providerName, model.modelCode, model.modelName,
                model.contextWindow, model.maxOutputTokens, Integer.valueOf(1).equals(model.supportStream),
                Integer.valueOf(1).equals(model.supportVision), Integer.valueOf(1).equals(model.isDefault));
    }
}
