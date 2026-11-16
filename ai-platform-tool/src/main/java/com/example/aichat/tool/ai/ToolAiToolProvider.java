package com.example.aichat.tool.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.ai.tool.AiToolDefinition;
import com.example.aichat.ai.tool.AiToolExecutionResult;
import com.example.aichat.ai.tool.AiToolProvider;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ToolAiToolProvider implements AiToolProvider {
    private static final String EXECUTE_PERMISSION = "tool:execute";
    private static final Pattern MODEL_TOOL_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };

    private final ToolDefinitionMapper toolMapper;
    private final ToolExecutionService executionService;
    private final McpServerManagementService serverService;
    private final ToolConfigCodec configCodec;
    private final HttpToolSchemaGenerator schemaGenerator;
    private final ObjectMapper objectMapper;

    public ToolAiToolProvider(ToolDefinitionMapper toolMapper, ToolExecutionService executionService,
                              McpServerManagementService serverService, ToolConfigCodec configCodec,
                              HttpToolSchemaGenerator schemaGenerator, ObjectMapper objectMapper) {
        this.toolMapper = toolMapper;
        this.executionService = executionService;
        this.serverService = serverService;
        this.configCodec = configCodec;
        this.schemaGenerator = schemaGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiToolContext prepare(Long userId, String requestId, List<String> toolCodes) {
        List<String> codes = normalizeCodes(toolCodes);
        if (codes.isEmpty()) {
            return new AiToolContext(userId, requestId, List.of(), this::execute);
        }
        StpUtil.switchTo(userId, () -> StpUtil.checkPermission(EXECUTE_PERMISSION));
        List<ToolDefinition> definitions = toolMapper.selectList(new LambdaQueryWrapper<ToolDefinition>()
                .in(ToolDefinition::getToolCode, codes));
        Map<String, ToolDefinition> byCode = definitions.stream()
                .collect(java.util.stream.Collectors.toMap(ToolDefinition::getToolCode,
                        definition -> definition, (first, ignored) -> first, LinkedHashMap::new));
        List<AiToolDefinition> aiDefinitions = new ArrayList<>();
        for (String code : codes) {
            ToolDefinition definition = byCode.get(code);
            if (definition == null) {
                throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
            }
            validateExecutable(definition);
            JsonNode schema = schema(definition);
            String description = StringUtils.hasText(definition.getDescription())
                    ? definition.getToolName() + ": " + definition.getDescription()
                    : definition.getToolName();
            aiDefinitions.add(new AiToolDefinition(definition.getToolCode(), definition.getToolName(),
                    description, schema));
        }
        return new AiToolContext(userId, requestId, aiDefinitions,
                (toolCode, arguments) -> execute(userId, requestId, toolCode, arguments));
    }

    private AiToolExecutionResult execute(String toolCode, Map<String, Object> arguments) {
        return AiToolExecutionResult.failure(ErrorCode.TOOL_NOT_FOUND.code(),
                "Requested tool is not available");
    }

    private AiToolExecutionResult execute(Long userId, String requestId, String toolCode,
                                          Map<String, Object> arguments) {
        try {
            Map<String, Object> copiedArguments = objectMapper.convertValue(
                    arguments == null ? Map.of() : arguments, ARGUMENTS_TYPE);
            ToolExecutionResult result = executionService.executeAsUser(userId,
                    new ToolExecuteRequest(toolCode, copiedArguments, requestId));
            return AiToolExecutionResult.success(result.data());
        } catch (BusinessException exception) {
            return AiToolExecutionResult.failure(exception.errorCode().code(), safeMessage(exception.getMessage()));
        } catch (RuntimeException exception) {
            return AiToolExecutionResult.failure(ErrorCode.TOOL_EXECUTION_FAILED.code(),
                    ErrorCode.TOOL_EXECUTION_FAILED.message());
        }
    }

    private void validateExecutable(ToolDefinition definition) {
        if (!Integer.valueOf(1).equals(definition.getStatus())) {
            throw new BusinessException(ErrorCode.TOOL_DISABLED);
        }
        if (definition.getAvailability() == ToolAvailability.STALE) {
            throw new BusinessException(ErrorCode.TOOL_STALE);
        }
        if (definition.getToolType() == ToolType.MCP) {
            if (definition.getMcpServerId() == null
                    || !Integer.valueOf(1).equals(serverService.getRequired(definition.getMcpServerId()).getStatus())) {
                throw new BusinessException(ErrorCode.TOOL_DISABLED, "MCP server is disabled");
            }
        }
    }

    private JsonNode schema(ToolDefinition definition) {
        try {
            if (definition.getToolType() == ToolType.HTTP) {
                HttpToolConfig config = configCodec.decode(definition.getHttpConfig());
                return schemaGenerator.generate(config);
            }
            JsonNode schema = objectMapper.readTree(definition.getInputSchema());
            if (schema == null || !schema.isObject()
                    || !schema.path("type").isTextual()
                    || !"object".equals(schema.path("type").textValue())) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                        "MCP tool input schema must be an object schema");
            }
            JsonNode properties = schema.get("properties");
            if (properties != null && !properties.isObject()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                        "MCP tool input schema properties must be an object");
            }
            JsonNode required = schema.get("required");
            if (required != null && !required.isArray()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                        "MCP tool input schema required must be an array");
            }
            return schema;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Tool input schema is invalid", exception);
        }
    }

    private List<String> normalizeCodes(List<String> toolCodes) {
        if (toolCodes == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : toolCodes) {
            if (!StringUtils.hasText(code)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tool code must not be blank");
            }
            String value = code.trim();
            if (value.length() > 100) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tool code is too long");
            }
            if (!MODEL_TOOL_NAME.matcher(value).matches()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID,
                        "Tool code must contain only letters, digits, hyphens, and underscores and be at most 64 characters");
            }
            normalized.add(value);
        }
        if (normalized.size() > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "At most 20 tool codes are allowed");
        }
        return List.copyOf(normalized);
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return ErrorCode.TOOL_EXECUTION_FAILED.message();
        }
        String sanitized = message.replaceAll("(?i)(authorization|password|token|api[-_]?key)\\s*[:=]\\s*[^,\\s]+",
                "$1=<redacted>");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }
}
