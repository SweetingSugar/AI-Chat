package com.example.aichat.tool.service.impl;

import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolMcpServer;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolAuthType;
import com.example.aichat.tool.enums.ToolInvocationStatus;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.executor.ToolExecutionContext;
import com.example.aichat.tool.executor.ToolExecutor;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolInvocationLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToolExecutionServiceImplTest {
    @Mock
    private ToolDefinitionMapper toolMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ToolInvocationLogService logService;
    @Mock
    private McpServerManagementService serverService;

    private ToolExecutor executor;
    private ToolExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        executor = mock(ToolExecutor.class);
        service = new ToolExecutionServiceImpl(toolMapper, currentUserProvider, serverService,
                logService, List.of(executor));
    }

    @Test
    void executesEnabledToolWithCurrentUserAndWritesSuccessAudit() {
        ToolDefinition definition = definition();
        when(toolMapper.selectOne(any())).thenReturn(definition);
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, "alice"));
        when(executor.toolType()).thenReturn(ToolType.HTTP);
        when(executor.execute(any(), any())).thenReturn(new ToolExecutionResult(
                "client-1", "orders", ToolType.HTTP, true, 200, Map.of("ok", true), 12));

        ToolExecutionResult result = service.execute(new ToolExecuteRequest("orders", Map.of("id", 1), "client-1"));

        assertThat(result.requestId()).isEqualTo("client-1");
        ArgumentCaptor<com.example.aichat.tool.entity.ToolInvocationLog> captor =
                ArgumentCaptor.forClass(com.example.aichat.tool.entity.ToolInvocationLog.class);
        verify(logService).record(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ToolInvocationStatus.SUCCESS);
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getErrorMessage()).isNull();
    }

    @Test
    void recordsFailureWithoutLoggingArguments() {
        ToolDefinition definition = definition();
        when(toolMapper.selectOne(any())).thenReturn(definition);
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, "alice"));
        when(executor.toolType()).thenReturn(ToolType.HTTP);
        when(executor.execute(any(), any())).thenThrow(new BusinessException(ErrorCode.TOOL_REMOTE_ERROR, "remote failed"));

        assertThatThrownBy(() -> service.execute(new ToolExecuteRequest("orders", Map.of("secret", "value"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.TOOL_REMOTE_ERROR);

        ArgumentCaptor<com.example.aichat.tool.entity.ToolInvocationLog> captor =
                ArgumentCaptor.forClass(com.example.aichat.tool.entity.ToolInvocationLog.class);
        verify(logService).record(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ToolInvocationStatus.FAILED);
        assertThat(captor.getValue().getErrorCode()).isEqualTo(ErrorCode.TOOL_REMOTE_ERROR.code());
        assertThat(captor.getValue().getErrorMessage()).doesNotContain("value");
    }

    @Test
    void rejectsDisabledToolBeforeCallingExecutor() {
        ToolDefinition definition = definition();
        definition.setStatus(0);
        when(toolMapper.selectOne(any())).thenReturn(definition);
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(42L, "alice"));

        assertThatThrownBy(() -> service.execute(new ToolExecuteRequest("orders", Map.of(), null)))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.TOOL_DISABLED);
    }

    private ToolDefinition definition() {
        ToolDefinition definition = new ToolDefinition();
        definition.setId(8L);
        definition.setToolCode("orders");
        definition.setToolType(ToolType.HTTP);
        definition.setStatus(1);
        definition.setAvailability(ToolAvailability.AVAILABLE);
        definition.setAuthType(ToolAuthType.NONE);
        return definition;
    }
}
