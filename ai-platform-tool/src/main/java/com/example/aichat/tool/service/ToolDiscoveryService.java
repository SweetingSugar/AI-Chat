package com.example.aichat.tool.service;

import com.example.aichat.tool.dto.management.ToolDiscoveryResult;

public interface ToolDiscoveryService {
    ToolDiscoveryResult discover(Long serverId);
}
