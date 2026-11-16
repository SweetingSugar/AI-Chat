package com.example.aichat.tool.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.example.aichat.ai.tool.AiToolContext;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.dto.management.HttpToolConfig;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolHttpMethod;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.validation.ToolConfigCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ToolAiToolProviderTest {
    private final ToolDefinitionMapper toolMapper = mock(ToolDefinitionMapper.class);
    private final ToolExecutionService executionService = mock(ToolExecutionService.class);
    private final McpServerManagementService serverService = mock(McpServerManagementService.class);
    private final ToolConfigCodec configCodec = mock(ToolConfigCodec.class);
    private final HttpToolSchemaGenerator schemaGenerator = mock(HttpToolSchemaGenerator.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolAiToolProvider provider = new ToolAiToolProvider(toolMapper, executionService,
            serverService, configCodec, schemaGenerator, objectMapper);

    @BeforeEach
    void initializeMybatisLambdaMetadata() {
        if (TableInfoHelper.getTableInfo(ToolDefinition.class) == null) {
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                    ToolDefinition.class);
        }
    }

    @Test
    void preparesToolsWithoutServletSaTokenContext() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        when(toolMapper.selectList(any())).thenReturn(List.of(enabledHttpDefinition()));
        when(configCodec.decode(any())).thenReturn(new HttpToolConfig(
                "https://example.com", ToolHttpMethod.GET, Map.of(), Map.of("q", "{{query}}"), null));
        when(schemaGenerator.generate(any())).thenReturn(schema);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(42L, "tool:execute")).thenReturn(true);

            AiToolContext context = provider.prepare(42L, "request-1", List.of("weather"));

            assertThat(context.definitions()).hasSize(1);
            assertThat(context.definitions().get(0).code()).isEqualTo("weather");
            stp.verify(() -> StpUtil.hasPermission(42L, "tool:execute"));
            stp.verifyNoMoreInteractions();
        }
    }

    @Test
    void rejectsUserWithoutToolExecutionPermission() {
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(() -> StpUtil.hasPermission(42L, "tool:execute")).thenReturn(false);

            assertThatThrownBy(() -> provider.prepare(42L, "request-1", List.of("weather")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(exception -> ((BusinessException) exception).errorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }
    }

    private ToolDefinition enabledHttpDefinition() {
        ToolDefinition definition = new ToolDefinition();
        definition.setToolCode("weather");
        definition.setToolName("Weather");
        definition.setToolType(ToolType.HTTP);
        definition.setStatus(1);
        definition.setAvailability(ToolAvailability.AVAILABLE);
        definition.setAuthType(ToolAuthType.NONE);
        definition.setHttpConfig("{}");
        return definition;
    }
}
