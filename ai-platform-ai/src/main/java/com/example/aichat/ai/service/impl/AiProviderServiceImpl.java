package com.example.aichat.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.dto.response.AiProviderManagementDTO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.exception.AiModelCallException;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.mapper.AiProviderMapper;
import com.example.aichat.ai.service.AiProviderService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiProviderServiceImpl implements AiProviderService {
    private final AiProviderMapper mapper;
    private final AiModelMapper modelMapper;
    private final AiCredentialCipher credentialCipher;

    public AiProviderServiceImpl(AiProviderMapper mapper, AiModelMapper modelMapper, AiCredentialCipher credentialCipher) {
        this.mapper = mapper;
        this.modelMapper = modelMapper;
        this.credentialCipher = credentialCipher;
    }

    @Override
    public List<AiProviderManagementDTO> listManagement() {
        return mapper.selectList(new LambdaQueryWrapper<AiProvider>()
                        .orderByAsc(AiProvider::getSort, AiProvider::getId))
                .stream().map(this::toManagementDto).toList();
    }

    @Override
    public AiProviderManagementDTO getManagementRequired(Long providerId) {
        return toManagementDto(getRequired(providerId));
    }

    @Override
    public AiProvider getRequired(Long providerId) {
        AiProvider provider = mapper.selectById(providerId);
        if (provider == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "AI provider not found");
        return provider;
    }

    @Override
    @Transactional
    public AiProviderManagementDTO create(AiProviderUpsertRequest request) {
        assertCodeAvailable(request.providerCode(), null);
        if (requiresCredential(request.providerType()) && !StringUtils.hasText(request.apiKey())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required when creating a provider");
        }
        AiProvider provider = copy(request, new AiProvider());
        provider.setApiKeyCiphertext(StringUtils.hasText(request.apiKey())
                ? credentialCipher.encrypt(request.apiKey().trim())
                : null);
        provider.setCredentialVersion(1);
        mapper.insert(provider);
        return toManagementDto(provider);
    }

    @Override
    @Transactional
    public AiProviderManagementDTO update(Long providerId, AiProviderUpsertRequest request) {
        AiProvider provider = getRequired(providerId);
        assertCodeAvailable(request.providerCode(), providerId);
        if (requiresCredential(request.providerType())
                && !StringUtils.hasText(request.apiKey())
                && !StringUtils.hasText(provider.getApiKeyCiphertext())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "API key is required for this provider type");
        }
        copy(request, provider);
        if (StringUtils.hasText(request.apiKey())) {
            provider.setApiKeyCiphertext(credentialCipher.encrypt(request.apiKey().trim()));
            provider.setCredentialVersion(provider.getCredentialVersion() == null ? 1 : provider.getCredentialVersion() + 1);
        }
        mapper.updateById(provider);
        return toManagementDto(provider);
    }

    @Override
    @Transactional
    public void delete(Long providerId) {
        getRequired(providerId);
        long modelCount = modelMapper.selectCount(new LambdaQueryWrapper<AiModel>().eq(AiModel::getProviderId, providerId));
        if (modelCount > 0) throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "AI provider still has models");
        mapper.deleteById(providerId);
    }

    @Override
    public AiProvider requireEnabled(Long providerId) {
        AiProvider provider = mapper.selectById(providerId);
        if (provider == null || !Integer.valueOf(1).equals(provider.status)) {
            throw new AiModelCallException("AI provider is not enabled");
        }
        return provider;
    }

    private boolean requiresCredential(AiProviderType providerType) {
        return providerType != AiProviderType.OLLAMA;
    }

    private void assertCodeAvailable(String providerCode, Long excludedId) {
        LambdaQueryWrapper<AiProvider> query = new LambdaQueryWrapper<AiProvider>().eq(AiProvider::getProviderCode, providerCode);
        if (excludedId != null) query.ne(AiProvider::getId, excludedId);
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "AI provider code already exists");
        }
    }

    private AiProvider copy(AiProviderUpsertRequest request, AiProvider provider) {
        provider.setProviderCode(request.providerCode().trim());
        provider.setProviderName(request.providerName().trim());
        provider.setProviderType(request.providerType());
        provider.setBaseUrl(request.baseUrl().trim());
        provider.setConnectTimeout(request.connectTimeout());
        provider.setReadTimeout(request.readTimeout());
        provider.setStatus(request.status() == null ? 1 : request.status());
        provider.setSort(request.sort() == null ? 0 : request.sort());
        provider.setRemark(StringUtils.hasText(request.remark()) ? request.remark().trim() : null);
        return provider;
    }

    private AiProviderManagementDTO toManagementDto(AiProvider provider) {
        return new AiProviderManagementDTO(provider.getId(), provider.getProviderCode(), provider.getProviderName(),
                provider.getProviderType(), provider.getBaseUrl(), provider.getConnectTimeout(), provider.getReadTimeout(),
                provider.getStatus(), StringUtils.hasText(provider.getApiKeyCiphertext()), provider.getCredentialVersion(),
                provider.getSort(), provider.getRemark());
    }
}
