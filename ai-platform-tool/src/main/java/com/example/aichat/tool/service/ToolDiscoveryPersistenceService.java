package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.management.ToolDiscoveryResult;
import com.example.aichat.tool.entity.ToolMcpServer;

import java.util.List;

public interface ToolDiscoveryPersistenceService {
    ToolDiscoveryResult synchronize(ToolMcpServer server, List<DiscoveredTool> tools);

    void markFailure(ToolMcpServer server, String message);
}
