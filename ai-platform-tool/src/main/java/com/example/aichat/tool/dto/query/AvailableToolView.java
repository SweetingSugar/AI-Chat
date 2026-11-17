package com.example.aichat.tool.dto.query;

import com.example.aichat.tool.enums.ToolType;
import com.fasterxml.jackson.databind.JsonNode;

public record AvailableToolView(
        String toolCode,
        String toolName,
        String description,
        ToolType toolType,
        JsonNode inputSchema) {
}
