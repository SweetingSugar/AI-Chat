package com.example.aichat.tool.ai;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HttpToolSchemaGenerator {
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\{\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*)}}"
    );

    private final ObjectMapper objectMapper;

    public HttpToolSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(HttpToolConfig config) {
        SchemaNode root = SchemaNode.object();
        scanMap(config.queryParams(), root, ValueKind.STRING);
        scanMap(config.headers(), root, ValueKind.STRING);
        if (config.bodyTemplate() != null) {
            scanNode(config.bodyTemplate(), root);
        }
        return root.toJson(objectMapper);
    }

    private void scanMap(Map<String, String> values, SchemaNode root, ValueKind kind) {
        if (values == null) {
            return;
        }
        for (String value : values.values()) {
            scanText(value, root, kind);
        }
    }

    private void scanNode(JsonNode node, SchemaNode root) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> scanNode(entry.getValue(), root));
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> scanNode(child, root));
            return;
        }
        if (node.isTextual()) {
            Matcher whole = PLACEHOLDER.matcher(node.textValue());
            if (whole.matches()) {
                add(root, whole.group(1), ValueKind.ANY);
            } else {
                scanText(node.textValue(), root, ValueKind.STRING);
            }
        }
    }

    private void scanText(String value, SchemaNode root, ValueKind kind) {
        if (value == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            add(root, matcher.group(1), kind);
        }
    }

    private void add(SchemaNode root, String path, ValueKind kind) {
        SchemaNode current = root;
        String[] segments = path.split("\\.");
        for (int index = 0; index < segments.length; index++) {
            String segment = segments[index];
            if (current.kind != ValueKind.OBJECT) {
                throw invalid("Conflicting tool parameter paths");
            }
            SchemaNode child = current.children.computeIfAbsent(segment, ignored -> SchemaNode.unset());
            if (index == segments.length - 1) {
                child.mergeValue(kind);
            } else {
                child.requireObject();
            }
            current = child;
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, message);
    }

    private enum ValueKind {
        UNSET,
        OBJECT,
        STRING,
        ANY
    }

    private static final class SchemaNode {
        private ValueKind kind;
        private final Map<String, SchemaNode> children = new LinkedHashMap<>();

        private SchemaNode(ValueKind kind) {
            this.kind = kind;
        }

        static SchemaNode object() {
            return new SchemaNode(ValueKind.OBJECT);
        }

        static SchemaNode unset() {
            return new SchemaNode(ValueKind.UNSET);
        }

        void requireObject() {
            if (kind == ValueKind.STRING || kind == ValueKind.ANY) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Conflicting tool parameter paths");
            }
            kind = ValueKind.OBJECT;
        }

        void mergeValue(ValueKind valueKind) {
            if (!children.isEmpty()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Conflicting tool parameter paths");
            }
            if (kind == ValueKind.OBJECT) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Conflicting tool parameter paths");
            }
            if (kind == ValueKind.UNSET || valueKind == ValueKind.ANY) {
                kind = valueKind;
            } else {
                kind = ValueKind.STRING;
            }
        }

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode schema = mapper.createObjectNode();
            if (kind == ValueKind.STRING) {
                schema.put("type", "string");
                return schema;
            }
            if (kind == ValueKind.ANY || kind == ValueKind.UNSET) {
                return schema;
            }
            schema.put("type", "object");
            ObjectNode properties = mapper.createObjectNode();
            ArrayNode required = mapper.createArrayNode();
            children.forEach((name, child) -> {
                properties.set(name, child.toJson(mapper));
                required.add(name);
            });
            schema.set("properties", properties);
            schema.set("required", required);
            schema.put("additionalProperties", false);
            return schema;
        }
    }
}
