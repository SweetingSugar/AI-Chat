package com.example.aichat.ai.service;

import com.example.aichat.ai.dto.response.AiModelUsageDTO;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.dto.response.AiUsageSummaryDTO;
import com.example.aichat.ai.entity.AiCallLog;
import com.example.aichat.ai.enums.AiCallStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface AiCallLogService {
    AiCallLog start(AiCallLog log);

    void finish(Long id, AiCallStatus status, AiTokenUsage usage, String finishReason, String errorCode, String errorMessage);

    AiUsageSummaryDTO summarize(LocalDateTime from, LocalDateTime to);

    List<AiModelUsageDTO> summarizeByModel(LocalDateTime from, LocalDateTime to);
}
