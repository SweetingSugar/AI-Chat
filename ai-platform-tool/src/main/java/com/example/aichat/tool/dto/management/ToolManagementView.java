package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public record ToolManagementView(
        Long id,
        String toolCode,
        String toolName,
        String description,
        ToolType toolType,
        ToolSource source,
        Integer status,
        ToolAvailability availability,
        Long mcpServerId,
        String remoteToolName,
        ToolAuthType authType,
        String authHeaderName,
        JsonNode httpConfig,
        JsonNode inputSchema,
        boolean hasCredential,
        Integer credentialVersion,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
