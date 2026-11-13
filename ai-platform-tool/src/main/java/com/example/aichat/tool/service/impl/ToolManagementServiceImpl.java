package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.management.ToolManagementView;
import com.example.aichat.tool.dto.management.ToolStatusRequest;
import com.example.aichat.tool.dto.management.ToolUpsertRequest;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.ToolManagementService;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ToolManagementServiceImpl implements ToolManagementService {
    private final ToolDefinitionMapper mapper;
    private final ToolConfigCodec configCodec;
    private final ToolCredentialHeaders credentialHeaders;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ToolProperties properties;
    private final ObjectMapper objectMapper;

    public ToolManagementServiceImpl(ToolDefinitionMapper mapper,
                                     ToolConfigCodec configCodec,
                                     ToolCredentialHeaders credentialHeaders,
                                     UrlSafetyValidator urlSafetyValidator,
                                     ToolProperties properties,
                                     ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.configCodec = configCodec;
        this.credentialHeaders = credentialHeaders;
        this.urlSafetyValidator = urlSafetyValidator;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ToolManagementView> listManagement() {
        return mapper.selectList(new LambdaQueryWrapper<ToolDefinition>()
                        .orderByAsc(ToolDefinition::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    public ToolManagementView getManagementRequired(Long toolId) {
        return toView(getRequired(toolId));
    }

    @Override
    public ToolDefinition getRequired(Long toolId) {
        ToolDefinition tool = mapper.selectById(toolId);
        if (tool == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        return tool;
    }

    @Override
    @Transactional
    public ToolManagementView create(ToolUpsertRequest request) {
        assertCodeAvailable(request.toolCode(), null);
        ToolDefinition tool = copy(request, new ToolDefinition(), true);
        mapper.insert(tool);
        return toView(tool);
    }

    @Override
    @Transactional
    public ToolManagementView update(Long toolId, ToolUpsertRequest request) {
        ToolDefinition tool = getRequired(toolId);
        assertCodeAvailable(request.toolCode(), toolId);
        copy(request, tool, false);
        mapper.updateById(tool);
        return toView(tool);
    }

    @Override
    @Transactional
    public void delete(Long toolId) {
        getRequired(toolId);
        mapper.deleteById(toolId);
    }

    @Override
    @Transactional
    public ToolManagementView updateStatus(Long toolId, ToolStatusRequest request) {
        ToolDefinition tool = getRequired(toolId);
        tool.setStatus(request.status());
        mapper.updateById(tool);
        return toView(tool);
    }

    private ToolDefinition copy(ToolUpsertRequest request, ToolDefinition tool, boolean creating) {
        if (request.toolType() != ToolType.HTTP || request.httpConfig() == null) {
            throw invalid("Only HTTP tools can be managed manually");
        }
        if (!StringUtils.hasText(request.toolCode()) || !StringUtils.hasText(request.toolName())) {
            throw invalid("Tool code and name are required");
        }
        if (request.authType() == null) {
            throw invalid("Authentication type is required");
        }
        validateHttpConfig(request);
        validateCredentials(request, tool, creating);

        tool.setToolCode(request.toolCode().trim());
        tool.setToolName(request.toolName().trim());
        tool.setDescription(StringUtils.hasText(request.description()) ? request.description().trim() : null);
        tool.setToolType(ToolType.HTTP);
        tool.setSource(ToolSource.MANUAL);
        tool.setAvailability(ToolAvailability.AVAILABLE);
        tool.setMcpServerId(null);
        tool.setRemoteToolName(null);
        tool.setHttpConfig(configCodec.encode(request.httpConfig()));
        tool.setInputSchema(null);
        tool.setAuthType(request.authType());
        tool.setAuthHeaderName(request.authType() == ToolAuthType.API_KEY
                ? request.authHeaderName().trim() : null);
        tool.setStatus(request.status() == null ? (creating ? 0 : tool.getStatus()) : request.status());
        if (tool.getStatus() == null) {
            tool.setStatus(0);
        }
        if (tool.getCredentialVersion() == null) {
            tool.setCredentialVersion(1);
        }
        applyCredentials(request, tool, creating);
        return tool;
    }

    private void validateHttpConfig(ToolUpsertRequest request) {
        urlSafetyValidator.validate(request.httpConfig().url());
        Map<String, String> headers = request.httpConfig().headers() == null
                ? Map.of() : request.httpConfig().headers();
        Map<String, String> secretHeaders = request.secretHeaders() == null
                ? Map.of() : request.secretHeaders();
        int authHeaderCount = request.authType() == ToolAuthType.NONE ? 0 : 1;
        if (headers.size() + secretHeaders.size() + authHeaderCount > properties.getMaxHeaderCount()) {
            throw invalid("Too many headers");
        }
        String configuredAuthHeader = request.authType() == ToolAuthType.BEARER
                ? "authorization" : request.authHeaderName();
        for (String headerName : headers.keySet()) {
            String normalized = headerName == null ? "" : headerName.toLowerCase(Locale.ROOT);
            if (normalized.equals("authorization") || normalized.equals("proxy-authorization")
                    || (configuredAuthHeader != null && normalized.equals(configuredAuthHeader.toLowerCase(Locale.ROOT)))) {
                throw invalid("Authentication headers must use protected credential fields");
            }
        }
        if (request.httpConfig().bodyTemplate() != null
                && request.httpConfig().bodyTemplate().toString().getBytes(StandardCharsets.UTF_8).length
                > properties.getMaxBodyBytes()) {
            throw invalid("HTTP body template is too large");
        }
    }

    private void validateCredentials(ToolUpsertRequest request, ToolDefinition tool, boolean creating) {
        if (request.authType() == ToolAuthType.API_KEY
                && !StringUtils.hasText(request.authHeaderName())) {
            throw invalid("API key header name is required");
        }
        if (request.authType() != ToolAuthType.NONE
                && creating
                && !StringUtils.hasText(request.credential())) {
            throw invalid("Credential is required");
        }
        if (request.authType() != ToolAuthType.NONE
                && !creating
                && !StringUtils.hasText(request.credential())
                && !StringUtils.hasText(tool.getCredentialCiphertext())) {
            throw invalid("Credential is required");
        }
    }

    private void applyCredentials(ToolUpsertRequest request, ToolDefinition tool, boolean creating) {
        if (request.authType() == ToolAuthType.NONE) {
            tool.setCredentialCiphertext(null);
        } else if (StringUtils.hasText(request.credential())) {
            tool.setCredentialCiphertext(credentialHeaders.encryptCredential(request.credential().trim()));
            tool.setCredentialVersion(tool.getCredentialVersion() == null ? 1 : tool.getCredentialVersion() + (creating ? 0 : 1));
        }
        if (creating || request.secretHeaders() != null) {
            tool.setSecretHeadersCiphertext(credentialHeaders.encryptSecretHeaders(request.secretHeaders()));
        }
    }

    private void assertCodeAvailable(String toolCode, Long excludedId) {
        if (!StringUtils.hasText(toolCode)) {
            throw invalid("Tool code is required");
        }
        LambdaQueryWrapper<ToolDefinition> query = new LambdaQueryWrapper<ToolDefinition>()
                .eq(ToolDefinition::getToolCode, toolCode.trim());
        if (excludedId != null) {
            query.ne(ToolDefinition::getId, excludedId);
        }
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Tool code already exists");
        }
    }

    private ToolManagementView toView(ToolDefinition tool) {
        JsonNode httpConfig = null;
        if (StringUtils.hasText(tool.getHttpConfig())) {
            try {
                httpConfig = objectMapper.readTree(tool.getHttpConfig());
            } catch (Exception exception) {
                throw invalid("Stored HTTP configuration is invalid");
            }
        }
        return new ToolManagementView(
                tool.getId(), tool.getToolCode(), tool.getToolName(), tool.getDescription(), tool.getToolType(),
                tool.getSource(), tool.getStatus(), tool.getAvailability(), tool.getMcpServerId(),
                tool.getRemoteToolName(), tool.getAuthType(), tool.getAuthHeaderName(), httpConfig,
                parseSchema(tool.getInputSchema()),
                StringUtils.hasText(tool.getCredentialCiphertext()) || StringUtils.hasText(tool.getSecretHeadersCiphertext()),
                tool.getCredentialVersion(), tool.getCreateTime(), tool.getUpdateTime());
    }

    private JsonNode parseSchema(String schema) {
        if (!StringUtils.hasText(schema)) {
            return null;
        }
        try {
            return objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw invalid("Stored tool schema is invalid");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, message);
    }
}
