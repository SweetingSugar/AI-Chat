package com.example.aichat.tool.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.execution.ToolExecutionResult;
import com.example.aichat.tool.entity.ToolDefinition;
import com.example.aichat.tool.entity.ToolInvocationLog;
import com.example.aichat.tool.enums.ToolAvailability;
import com.example.aichat.tool.enums.ToolInvocationStatus;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.executor.ToolExecutionContext;
import com.example.aichat.tool.executor.ToolExecutor;
import com.example.aichat.tool.mapper.ToolDefinitionMapper;
import com.example.aichat.tool.service.McpServerManagementService;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.service.ToolInvocationLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ToolExecutionServiceImpl implements ToolExecutionService {
    private final ToolDefinitionMapper toolMapper;
    private final CurrentUserProvider currentUserProvider;
    private final McpServerManagementService serverService;
    private final ToolInvocationLogService logService;
    private final List<ToolExecutor> executors;

    public ToolExecutionServiceImpl(ToolDefinitionMapper toolMapper,
                                    CurrentUserProvider currentUserProvider,
                                    McpServerManagementService serverService,
                                    ToolInvocationLogService logService,
                                    List<ToolExecutor> executors) {
        this.toolMapper = toolMapper;
        this.currentUserProvider = currentUserProvider;
        this.serverService = serverService;
        this.logService = logService;
        this.executors = executors;
    }

    @Override
    @Transactional
    public ToolExecutionResult execute(ToolExecuteRequest request) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        String requestId = StringUtils.hasText(request.requestId()) ? request.requestId().trim() : UUID.randomUUID().toString();
        ToolDefinition definition = null;
        LocalDateTime startedAt = LocalDateTime.now();
        long startedNanos = System.nanoTime();
        try {
            definition = toolMapper.selectOne(new LambdaQueryWrapper<ToolDefinition>()
                    .eq(ToolDefinition::getToolCode, request.toolCode()));
            if (definition == null) {
                throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
            }
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
            ToolType requestedType = definition.getToolType();
            ToolExecutor executor = executors.stream()
                    .filter(candidate -> candidate.toolType() == requestedType)
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Tool executor is unavailable"));
            ToolExecutionResult result = executor.execute(definition, new ToolExecutionContext(requestId, request.arguments()));
            ToolExecutionResult normalized = new ToolExecutionResult(requestId, definition.getToolCode(),
                    requestedType, result.success(), result.statusCode(), result.data(),
                    Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
            recordSuccess(definition, user.id(), requestId, startedAt, normalized);
            return normalized;
        } catch (BusinessException exception) {
            if (definition != null) {
                recordFailure(definition, user.id(), requestId, startedAt, startedNanos, exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            BusinessException failure = new BusinessException(ErrorCode.TOOL_EXECUTION_FAILED,
                    "Tool execution failed", exception);
            if (definition != null) {
                recordFailure(definition, user.id(), requestId, startedAt, startedNanos, failure);
            }
            throw failure;
        }
    }

    private void recordSuccess(ToolDefinition definition, long userId, String requestId,
                               LocalDateTime startedAt, ToolExecutionResult result) {
        ToolInvocationLog log = baseLog(definition, userId, requestId, startedAt);
        log.setStatus(ToolInvocationStatus.SUCCESS);
        log.setDurationMs(result.durationMs());
        log.setEndTime(LocalDateTime.now());
        logService.record(log);
    }

    private void recordFailure(ToolDefinition definition, long userId, String requestId,
                               LocalDateTime startedAt, long startedNanos, BusinessException exception) {
        ToolInvocationLog log = baseLog(definition, userId, requestId, startedAt);
        log.setStatus(ToolInvocationStatus.FAILED);
        log.setDurationMs(Duration.ofNanos(System.nanoTime() - startedNanos).toMillis());
        log.setErrorCode(exception.errorCode().code());
        log.setErrorMessage(safeMessage(exception.getMessage()));
        log.setEndTime(LocalDateTime.now());
        logService.record(log);
    }

    private ToolInvocationLog baseLog(ToolDefinition definition, long userId, String requestId,
                                      LocalDateTime startedAt) {
        ToolInvocationLog log = new ToolInvocationLog();
        log.setRequestId(requestId);
        log.setToolId(definition.getId());
        log.setToolCode(definition.getToolCode());
        log.setUserId(userId);
        log.setToolType(definition.getToolType());
        log.setStartTime(startedAt);
        return log;
    }

    private String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String sanitized = message.replaceAll("(?i)(authorization|password|token|api[-_]?key)\\s*[:=]\\s*[^,\\s]+",
                "$1=<redacted>");
        return sanitized.length() <= 1000 ? sanitized : sanitized.substring(0, 1000);
    }
}
