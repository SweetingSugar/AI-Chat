package com.example.aichat.ai.tool;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Objects;

public record AiToolDefinition(String code, String name, String description, JsonNode inputSchema) {
    public AiToolDefinition {
        Objects.requireNonNull(code, "Tool code is required");
        Objects.requireNonNull(name, "Tool name is required");
        Objects.requireNonNull(inputSchema, "Tool input schema is required");
    }
}
