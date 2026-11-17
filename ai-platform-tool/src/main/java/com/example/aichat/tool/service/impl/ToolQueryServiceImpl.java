package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolQueryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

@Service
public class ToolQueryServiceImpl implements ToolQueryService {
    private final ToolDefinitionMapper mapper;
    private final McpServerManagementService serverService;
    private final ObjectMapper objectMapper;

    public ToolQueryServiceImpl(ToolDefinitionMapper mapper,
                                McpServerManagementService serverService,
                                ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.serverService = serverService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<AvailableToolView> listAvailable() {
        return mapper.selectList(new LambdaQueryWrapper<ToolDefinition>()
                        .eq(ToolDefinition::getStatus, 1)
                        .eq(ToolDefinition::getAvailability, ToolAvailability.AVAILABLE)
                        .orderByAsc(ToolDefinition::getId))
                .stream()
                .sorted(Comparator.comparing(ToolDefinition::getId))
                .filter(this::isEnabledAndAvailable)
                .filter(this::hasEnabledMcpServer)
                .map(this::toView)
                .toList();
    }

    private boolean isEnabledAndAvailable(ToolDefinition tool) {
        return !Integer.valueOf(1).equals(tool.getDeleted())
                && Integer.valueOf(1).equals(tool.getStatus())
                && tool.getAvailability() == ToolAvailability.AVAILABLE;
    }

    private boolean hasEnabledMcpServer(ToolDefinition tool) {
        if (tool.getToolType() != ToolType.MCP) {
            return true;
        }
        if (tool.getMcpServerId() == null) {
            return false;
        }
        try {
            ToolMcpServer server = serverService.getRequired(tool.getMcpServerId());
            return Integer.valueOf(1).equals(server.getStatus());
        } catch (BusinessException exception) {
            return false;
        }
    }

    private AvailableToolView toView(ToolDefinition tool) {
        return new AvailableToolView(tool.getToolCode(), tool.getToolName(), tool.getDescription(),
                tool.getToolType(), parseSchema(tool.getInputSchema()));
    }

    private JsonNode parseSchema(String schema) {
        if (!StringUtils.hasText(schema)) {
            return null;
        }
        try {
            return objectMapper.readTree(schema);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                    "Tool input schema is invalid", exception);
        }
    }
}
