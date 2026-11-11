package com.example.aichat.tool.dto.management;

import java.util.List;

public record ToolDiscoveryResult(
        Long serverId,
        int discovered,
        int created,
        int updated,
        int stale,
        List<ToolManagementView> tools) {
}
