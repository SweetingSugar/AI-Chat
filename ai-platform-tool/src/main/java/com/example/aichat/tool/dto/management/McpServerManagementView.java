package com.example.aichat.tool.dto.management;

import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolDiscoveryStatus;

import java.time.LocalDateTime;

public record McpServerManagementView(
        Long id,
        String serverCode,
        String serverName,
        String endpointUrl,
        ToolAuthType authType,
        Integer connectTimeout,
        Integer readTimeout,
        Integer status,
        boolean hasCredential,
        Integer credentialVersion,
        LocalDateTime lastDiscoveryTime,
        ToolDiscoveryStatus lastDiscoveryStatus,
        String lastDiscoveryError,
        LocalDateTime createTime,
        LocalDateTime updateTime) {
}
