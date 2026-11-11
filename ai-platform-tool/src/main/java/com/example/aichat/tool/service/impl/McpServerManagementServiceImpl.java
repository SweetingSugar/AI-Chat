package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.config.ToolProperties;
import com.example.aichat.tool.dto.management.McpServerManagementView;
import com.example.aichat.tool.dto.management.McpServerStatusRequest;
import com.example.aichat.tool.dto.management.McpServerUpsertRequest;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolDiscoveryStatus;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.mapper.ToolMcpServerMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.validation.ToolCredentialHeaders;
import com.example.aichat.tool.validation.UrlSafetyValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class McpServerManagementServiceImpl implements McpServerManagementService {
    private final ToolMcpServerMapper mapper;
    private final ToolDefinitionMapper toolMapper;
    private final ToolCredentialHeaders credentialHeaders;
    private final UrlSafetyValidator urlSafetyValidator;
    private final ToolProperties properties;

    public McpServerManagementServiceImpl(ToolMcpServerMapper mapper,
                                          ToolDefinitionMapper toolMapper,
                                          ToolCredentialHeaders credentialHeaders,
                                          UrlSafetyValidator urlSafetyValidator,
                                          ToolProperties properties) {
        this.mapper = mapper;
        this.toolMapper = toolMapper;
        this.credentialHeaders = credentialHeaders;
        this.urlSafetyValidator = urlSafetyValidator;
        this.properties = properties;
    }

    @Override
    public List<McpServerManagementView> listManagement() {
        return mapper.selectList(new LambdaQueryWrapper<ToolMcpServer>()
                        .orderByAsc(ToolMcpServer::getId))
                .stream().map(this::toView).toList();
    }

    @Override
    public McpServerManagementView getManagementRequired(Long serverId) {
        return toView(getRequired(serverId));
    }

    @Override
    public ToolMcpServer getRequired(Long serverId) {
        ToolMcpServer server = mapper.selectById(serverId);
        if (server == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "MCP server not found");
        }
        return server;
    }

    @Override
    @Transactional
    public McpServerManagementView create(McpServerUpsertRequest request) {
        assertCodeAvailable(request.serverCode(), null);
        ToolMcpServer server = copy(request, new ToolMcpServer(), true);
        mapper.insert(server);
        return toView(server);
    }

    @Override
    @Transactional
    public McpServerManagementView update(Long serverId, McpServerUpsertRequest request) {
        ToolMcpServer server = getRequired(serverId);
        assertCodeAvailable(request.serverCode(), serverId);
        copy(request, server, false);
        mapper.updateById(server);
        return toView(server);
    }

    @Override
    @Transactional
    public void delete(Long serverId) {
        getRequired(serverId);
        long toolCount = toolMapper.selectCount(new LambdaQueryWrapper<ToolDefinition>()
                .eq(ToolDefinition::getMcpServerId, serverId));
        if (toolCount > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "MCP server still has discovered tools");
        }
        mapper.deleteById(serverId);
    }

    @Override
    @Transactional
    public McpServerManagementView updateStatus(Long serverId, McpServerStatusRequest request) {
        ToolMcpServer server = getRequired(serverId);
        server.setStatus(request.status());
        mapper.updateById(server);
        return toView(server);
    }

    private ToolMcpServer copy(McpServerUpsertRequest request, ToolMcpServer server, boolean creating) {
        if (!StringUtils.hasText(request.serverCode()) || !StringUtils.hasText(request.serverName())
                || !StringUtils.hasText(request.endpointUrl()) || request.authType() == null) {
            throw invalid("MCP server code, name, endpoint and authentication type are required");
        }
        urlSafetyValidator.validate(request.endpointUrl());
        Map<String, String> secretHeaders = request.secretHeaders() == null ? Map.of() : request.secretHeaders();
        int authHeaderCount = request.authType() == ToolAuthType.NONE ? 0 : 1;
        if (secretHeaders.size() + authHeaderCount > properties.getMaxHeaderCount()) {
            throw invalid("Too many headers");
        }
        if (request.authType() == ToolAuthType.API_KEY && !StringUtils.hasText(request.authHeaderName())) {
            throw invalid("API key header name is required");
        }
        if (request.authType() != ToolAuthType.NONE && creating && !StringUtils.hasText(request.credential())) {
            throw invalid("Credential is required");
        }
        if (request.authType() != ToolAuthType.NONE && !creating
                && !StringUtils.hasText(request.credential())
                && !StringUtils.hasText(server.getCredentialCiphertext())) {
            throw invalid("Credential is required");
        }
        int connectTimeout = request.connectTimeout() == null ? properties.getDefaultConnectTimeout() : request.connectTimeout();
        int readTimeout = request.readTimeout() == null ? properties.getDefaultReadTimeout() : request.readTimeout();
        if (connectTimeout > properties.getMaxTimeout() || readTimeout > properties.getMaxTimeout()) {
            throw invalid("MCP timeout exceeds the configured maximum");
        }

        server.setServerCode(request.serverCode().trim());
        server.setServerName(request.serverName().trim());
        server.setEndpointUrl(request.endpointUrl().trim());
        server.setAuthType(request.authType());
        server.setAuthHeaderName(request.authType() == ToolAuthType.API_KEY ? request.authHeaderName().trim() : null);
        server.setConnectTimeout(connectTimeout);
        server.setReadTimeout(readTimeout);
        server.setStatus(request.status() == null ? (creating ? 0 : server.getStatus()) : request.status());
        if (server.getStatus() == null) {
            server.setStatus(0);
        }
        if (server.getCredentialVersion() == null) {
            server.setCredentialVersion(1);
        }
        if (request.authType() == ToolAuthType.NONE) {
            server.setCredentialCiphertext(null);
        } else if (StringUtils.hasText(request.credential())) {
            server.setCredentialCiphertext(credentialHeaders.encryptCredential(request.credential().trim()));
            if (!creating) {
                server.setCredentialVersion(server.getCredentialVersion() + 1);
            }
        }
        if (creating || request.secretHeaders() != null) {
            server.setSecretHeadersCiphertext(credentialHeaders.encryptSecretHeaders(request.secretHeaders()));
        }
        return server;
    }

    private void assertCodeAvailable(String serverCode, Long excludedId) {
        if (!StringUtils.hasText(serverCode)) {
            throw invalid("MCP server code is required");
        }
        LambdaQueryWrapper<ToolMcpServer> query = new LambdaQueryWrapper<ToolMcpServer>()
                .eq(ToolMcpServer::getServerCode, serverCode.trim());
        if (excludedId != null) {
            query.ne(ToolMcpServer::getId, excludedId);
        }
        if (mapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "MCP server code already exists");
        }
    }

    private McpServerManagementView toView(ToolMcpServer server) {
        return new McpServerManagementView(
                server.getId(), server.getServerCode(), server.getServerName(), server.getEndpointUrl(),
                server.getAuthType(), server.getConnectTimeout(), server.getReadTimeout(), server.getStatus(),
                StringUtils.hasText(server.getCredentialCiphertext()) || StringUtils.hasText(server.getSecretHeadersCiphertext()),
                server.getCredentialVersion(), server.getLastDiscoveryTime(), server.getLastDiscoveryStatus(),
                server.getLastDiscoveryError(), server.getCreateTime(), server.getUpdateTime());
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, message);
    }
}
