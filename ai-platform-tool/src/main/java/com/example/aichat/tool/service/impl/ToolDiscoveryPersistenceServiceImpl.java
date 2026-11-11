package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.management.ToolDiscoveryResult;
import com.example.aichat.tool.dto.management.ToolManagementView;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolDiscoveryStatus;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.mapper.ToolMcpServerMapper;
import com.example.aichat.tool.service.DiscoveredTool;
import com.example.aichat.tool.service.ToolDiscoveryPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ToolDiscoveryPersistenceServiceImpl implements ToolDiscoveryPersistenceService {
    private final ToolDefinitionMapper toolMapper;
    private final ToolMcpServerMapper serverMapper;
    private final ObjectMapper objectMapper;

    public ToolDiscoveryPersistenceServiceImpl(ToolDefinitionMapper toolMapper,
                                               ToolMcpServerMapper serverMapper,
                                               ObjectMapper objectMapper) {
        this.toolMapper = toolMapper;
        this.serverMapper = serverMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ToolDiscoveryResult synchronize(ToolMcpServer server, List<DiscoveredTool> tools) {
        Set<String> remoteNames = new HashSet<>();
        for (DiscoveredTool tool : tools) {
            if (tool == null || !StringUtils.hasText(tool.remoteName()) || !remoteNames.add(tool.remoteName())) {
                throw failure("MCP discovery returned an invalid or duplicate tool name");
            }
        }
        List<ToolDefinition> previous = new ArrayList<>(toolMapper.selectList(new LambdaQueryWrapper<ToolDefinition>()
                .eq(ToolDefinition::getMcpServerId, server.getId())));
        int created = 0;
        int updated = 0;
        for (DiscoveredTool discovered : tools) {
            ToolDefinition existing = toolMapper.selectOne(new LambdaQueryWrapper<ToolDefinition>()
                    .eq(ToolDefinition::getMcpServerId, server.getId())
                    .eq(ToolDefinition::getRemoteToolName, discovered.remoteName()));
            if (existing == null) {
                ToolDefinition createdTool = newSnapshot(server, discovered);
                toolMapper.insert(createdTool);
                previous.add(createdTool);
                created++;
            } else {
                existing.setToolName(limit(discovered.name(), 100, discovered.remoteName()));
                existing.setDescription(limit(discovered.description(), 1000, null));
                existing.setInputSchema(encode(discovered.inputSchema()));
                existing.setAvailability(ToolAvailability.AVAILABLE);
                toolMapper.updateById(existing);
                updated++;
            }
        }
        int stale = 0;
        for (ToolDefinition existing : previous) {
            if (existing.getRemoteToolName() != null && !remoteNames.contains(existing.getRemoteToolName())) {
                if (existing.getAvailability() != ToolAvailability.STALE) {
                    stale++;
                }
                existing.setAvailability(ToolAvailability.STALE);
                toolMapper.updateById(existing);
            }
        }
        server.setLastDiscoveryTime(LocalDateTime.now());
        server.setLastDiscoveryStatus(ToolDiscoveryStatus.SUCCESS);
        server.setLastDiscoveryError(null);
        serverMapper.updateById(server);
        return new ToolDiscoveryResult(server.getId(), tools.size(), created, updated, stale,
                previous.stream().filter(tool -> remoteNames.contains(tool.getRemoteToolName())).map(this::toView).toList());
    }

    @Override
    public void markFailure(ToolMcpServer server, String message) {
        server.setLastDiscoveryTime(LocalDateTime.now());
        server.setLastDiscoveryStatus(ToolDiscoveryStatus.FAILED);
        server.setLastDiscoveryError(limit(message, 1000, "MCP discovery failed"));
        serverMapper.updateById(server);
    }

    private ToolDefinition newSnapshot(ToolMcpServer server, DiscoveredTool discovered) {
        ToolDefinition tool = new ToolDefinition();
        tool.setToolCode(uniqueCode(server.getServerCode(), discovered.remoteName()));
        tool.setToolName(limit(discovered.name(), 100, discovered.remoteName()));
        tool.setDescription(limit(discovered.description(), 1000, null));
        tool.setToolType(ToolType.MCP);
        tool.setSource(ToolSource.DISCOVERED);
        tool.setStatus(0);
        tool.setAvailability(ToolAvailability.AVAILABLE);
        tool.setMcpServerId(server.getId());
        tool.setRemoteToolName(limit(discovered.remoteName(), 200, null));
        tool.setAuthType(ToolAuthType.NONE);
        tool.setCredentialVersion(1);
        tool.setInputSchema(encode(discovered.inputSchema()));
        return tool;
    }

    private String uniqueCode(String serverCode, String remoteName) {
        String base = normalize(serverCode) + "_" + normalize(remoteName);
        base = limit(base, 100, "mcp_tool");
        String candidate = base;
        int suffix = 2;
        while (toolMapper.selectCount(new LambdaQueryWrapper<ToolDefinition>()
                .eq(ToolDefinition::getToolCode, candidate)) > 0) {
            String suffixText = "_" + suffix++;
            candidate = limit(base, 100 - suffixText.length(), "mcp_tool") + suffixText;
        }
        return candidate;
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "mcp_tool" : normalized;
    }

    private String encode(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw failure("MCP tool schema is invalid");
        }
    }

    private ToolManagementView toView(ToolDefinition tool) {
        return new ToolManagementView(tool.getId(), tool.getToolCode(), tool.getToolName(), tool.getDescription(),
                tool.getToolType(), tool.getSource(), tool.getStatus(), tool.getAvailability(), tool.getMcpServerId(),
                tool.getRemoteToolName(), null, parse(tool.getInputSchema()), false, tool.getCredentialVersion(),
                tool.getCreateTime(), tool.getUpdateTime());
    }

    private JsonNode parse(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw failure("MCP tool schema is invalid");
        }
    }

    private String limit(String value, int max, String fallback) {
        String candidate = StringUtils.hasText(value) ? value.trim() : fallback;
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        return candidate.length() <= max ? candidate : candidate.substring(0, max);
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCode.TOOL_DISCOVERY_FAILED, message);
    }
}
