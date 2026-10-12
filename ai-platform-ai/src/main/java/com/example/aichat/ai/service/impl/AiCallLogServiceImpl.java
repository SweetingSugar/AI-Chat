package com.example.aichat.ai.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.aichat.ai.api.AiUsageQueryService;
import com.example.aichat.ai.dto.response.AiModelUsageDTO;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.dto.response.AiUsageSummaryDTO;
import com.example.aichat.ai.entity.AiCallLog;
import com.example.aichat.ai.enums.AiCallStatus;
import com.example.aichat.ai.mapper.AiCallLogMapper;
import com.example.aichat.ai.service.AiCallLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AiCallLogServiceImpl implements AiCallLogService, AiUsageQueryService {
    private final AiCallLogMapper mapper;

    public AiCallLogServiceImpl(AiCallLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public AiCallLog start(AiCallLog log) {
        log.status = AiCallStatus.RUNNING;
        log.startTime = LocalDateTime.now();
        mapper.insert(log);
        return log;
    }

    @Override
    public void finish(Long id, AiCallStatus status, AiTokenUsage usage, String finishReason, String errorCode, String errorMessage) {
        AiCallLog log = new AiCallLog();
        log.id = id;
        log.status = status;
        log.endTime = LocalDateTime.now();
        log.durationMs = java.time.Duration.between(mapper.selectById(id).startTime, log.endTime).toMillis();
        if (usage != null) {
            log.inputTokens = usage.inputTokens();
            log.outputTokens = usage.outputTokens();
            log.totalTokens = usage.totalTokens();
            log.usageSource = usage.source();
        }
        log.finishReason = finishReason;
        log.errorCode = errorCode;
        log.errorMessage = sanitize(errorMessage);
        mapper.updateById(log);
    }

    @Override
    public AiUsageSummaryDTO summarize(LocalDateTime from, LocalDateTime to) {
        List<AiCallLog> logs = logs(from, to);
        long success = logs.stream().filter(log -> log.status == AiCallStatus.SUCCESS).count();
        long failed = logs.stream().filter(log -> log.status == AiCallStatus.FAILED).count();
        long tokens = logs.stream().map(log -> log.totalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
        return new AiUsageSummaryDTO(logs.size(), success, failed, tokens);
    }

    @Override
    public List<AiModelUsageDTO> summarizeByModel(LocalDateTime from, LocalDateTime to) {
        Map<Long, List<AiCallLog>> groups = logs(from, to).stream().collect(Collectors.groupingBy(log -> log.modelId));
        return groups.entrySet().stream().map(entry -> {
            List<AiCallLog> logs = entry.getValue();
            long tokens = logs.stream().map(log -> log.totalTokens).filter(Objects::nonNull).mapToLong(Integer::longValue).sum();
            return new AiModelUsageDTO(entry.getKey(), logs.get(0).modelCode, logs.size(), tokens);
        }).toList();
    }

    private List<AiCallLog> logs(LocalDateTime from, LocalDateTime to) {
        return mapper.selectList(Wrappers.<AiCallLog>query().ge(from != null, "start_time", from).le(to != null, "start_time", to));
    }

    private String sanitize(String value) {
        return value == null ? null : value.replaceAll("(?i)(bearer|api[_-]?key)\\s+[^\\s]+", "$1 [REDACTED]");
    }
}
