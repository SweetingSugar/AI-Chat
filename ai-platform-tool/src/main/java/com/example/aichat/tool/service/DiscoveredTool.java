package com.example.aichat.tool.service;

import java.util.Map;

public record DiscoveredTool(
        String remoteName,
        String name,
        String description,
        Map<String, Object> inputSchema) {
}
