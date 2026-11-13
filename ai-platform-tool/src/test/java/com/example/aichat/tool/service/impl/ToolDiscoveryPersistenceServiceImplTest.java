package com.example.aichat.tool.service.impl;

import com.example.aichat.tool.dto.management.ToolDiscoveryResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolSource;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.mapper.ToolMcpServerMapper;
import com.example.aichat.tool.service.DiscoveredTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolDiscoveryPersistenceServiceImplTest {
    @Test
    void createsDisabledAvailableSnapshotAndMarksServerDiscoverySuccessful() {
        ToolDefinitionMapper toolMapper = mock(ToolDefinitionMapper.class);
        ToolMcpServerMapper serverMapper = mock(ToolMcpServerMapper.class);
        when(toolMapper.selectOne(any())).thenReturn(null);
        when(toolMapper.selectCount(any())).thenReturn(0L);
        when(toolMapper.selectList(any())).thenReturn(List.of());
        when(toolMapper.insert(any(ToolDefinition.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, ToolDefinition.class).setId(11L);
            return 1;
        });
        ToolMcpServer server = new ToolMcpServer();
        server.setId(4L);
        server.setServerCode("weather");

        ToolDiscoveryResult result = new ToolDiscoveryPersistenceServiceImpl(
                toolMapper, serverMapper, new ObjectMapper()).synchronize(server, List.of(
                new DiscoveredTool("weather_query", "Weather", "Current weather",
                        Map.of("type", "object"))));

        assertThat(result.created()).isEqualTo(1);
        assertThat(result.discovered()).isEqualTo(1);
        ArgumentCaptor<ToolDefinition> captor = ArgumentCaptor.forClass(ToolDefinition.class);
        verify(toolMapper).insert(captor.capture());
        ToolDefinition snapshot = captor.getValue();
        assertThat(snapshot.getToolCode()).isEqualTo("weather_weather_query");
        assertThat(snapshot.getToolType()).isEqualTo(ToolType.MCP);
        assertThat(snapshot.getSource()).isEqualTo(ToolSource.DISCOVERED);
        assertThat(snapshot.getStatus()).isZero();
        assertThat(snapshot.getAvailability()).isEqualTo(ToolAvailability.AVAILABLE);
        assertThat(result.tools()).hasSize(1);
        var view = result.tools().get(0);
        assertThat(view.authType()).isEqualTo(ToolAuthType.NONE);
        assertThat(view.authHeaderName()).isNull();
        verify(serverMapper).updateById(server);
    }

    @Test
    void preservesToolCodeAndAdminStatusWhenRefreshingExistingSnapshot() {
        ToolDefinition existing = new ToolDefinition();
        existing.setId(11L);
        existing.setToolCode("stable-code");
        existing.setToolCode("stable-code");
        existing.setToolName("Old name");
        existing.setStatus(1);
        existing.setAvailability(ToolAvailability.STALE);
        existing.setMcpServerId(4L);
        existing.setRemoteToolName("weather_query");
        ToolDefinitionMapper toolMapper = mock(ToolDefinitionMapper.class);
        ToolMcpServerMapper serverMapper = mock(ToolMcpServerMapper.class);
        when(toolMapper.selectOne(any())).thenReturn(existing);
        when(toolMapper.selectList(any())).thenReturn(List.of(existing));
        ToolMcpServer server = new ToolMcpServer();
        server.setId(4L);
        server.setServerCode("weather");

        ToolDiscoveryResult result = new ToolDiscoveryPersistenceServiceImpl(
                toolMapper, serverMapper, new ObjectMapper()).synchronize(server, List.of(
                new DiscoveredTool("weather_query", "New name", null, Map.of())));

        assertThat(result.updated()).isEqualTo(1);
        assertThat(existing.getToolCode()).isEqualTo("stable-code");
        assertThat(existing.getStatus()).isEqualTo(1);
        assertThat(existing.getAvailability()).isEqualTo(ToolAvailability.AVAILABLE);
        verify(toolMapper).updateById(existing);
    }
}
