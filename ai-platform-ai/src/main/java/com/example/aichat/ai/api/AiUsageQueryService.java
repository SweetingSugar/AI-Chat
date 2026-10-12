package com.example.aichat.ai.api;

import com.example.aichat.ai.dto.response.AiModelUsageDTO;
import com.example.aichat.ai.dto.response.AiUsageSummaryDTO;

import java.time.LocalDateTime;
import java.util.List;

public interface AiUsageQueryService {
    AiUsageSummaryDTO summarize(LocalDateTime from, LocalDateTime to);

    List<AiModelUsageDTO> summarizeByModel(LocalDateTime from, LocalDateTime to);
}
