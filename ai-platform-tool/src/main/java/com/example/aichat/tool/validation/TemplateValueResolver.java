package com.example.aichat.tool.validation;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateValueResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z_][A-Za-z0-9_]*(?:\\.(?:[A-Za-z_][A-Za-z0-9_]*|[0-9]+))*)}}" );

    private final ObjectMapper objectMapper;

    public TemplateValueResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode resolve(JsonNode template, Map<String, Object> arguments) {
        JsonNode argumentTree = objectMapper.valueToTree(arguments);
        return resolveNode(template, argumentTree);
    }

    private JsonNode resolveNode(JsonNode node, JsonNode arguments) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            var result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                result.set(field.getKey(), resolveNode(field.getValue(), arguments));
            }
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                result.add(resolveNode(child, arguments));
            }
            return result;
        }
        if (!node.isTextual()) {
            return node.deepCopy();
        }
        return resolveText(node.textValue(), arguments);
    }

    private JsonNode resolveText(String text, JsonNode arguments) {
        Matcher whole = PLACEHOLDER.matcher(text);
        if (whole.matches()) {
            return valueAt(arguments, whole.group(1));
        }

        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            JsonNode value = valueAt(arguments, matcher.group(1));
            matcher.appendReplacement(resolved, Matcher.quoteReplacement(asText(value)));
        }
        matcher.appendTail(resolved);
        if (resolved.indexOf("{{") >= 0 || resolved.indexOf("}}") >= 0) {
            throw invalidArgument("Malformed template expression");
        }
        return objectMapper.getNodeFactory().textNode(resolved.toString());
    }

    private JsonNode valueAt(JsonNode arguments, String path) {
        JsonNode current = arguments;
        for (String segment : path.split("\\.")) {
            if (current == null || (current.isObject() && !current.has(segment))) {
                throw invalidArgument("Missing template argument");
            }
            if (current.isObject()) {
                current = current.get(segment);
            } else if (current.isArray() && segment.chars().allMatch(Character::isDigit)) {
                int index;
                try {
                    index = Integer.parseInt(segment);
                } catch (NumberFormatException exception) {
                    throw invalidArgument("Invalid template argument path");
                }
                if (index >= current.size()) {
                    throw invalidArgument("Missing template argument");
                }
                current = current.get(index);
            } else {
                throw invalidArgument("Invalid template argument path");
            }
        }
        if (current == null || current.isMissingNode()) {
            throw invalidArgument("Missing template argument");
        }
        return current;
    }

    private String asText(JsonNode value) {
        return value.isValueNode() ? value.asText() : value.toString();
    }

    private BusinessException invalidArgument(String message) {
        return new BusinessException(ErrorCode.TOOL_ARGUMENT_INVALID, message);
    }
}
