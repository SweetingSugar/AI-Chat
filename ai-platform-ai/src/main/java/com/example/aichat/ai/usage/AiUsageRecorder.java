package com.example.aichat.ai.usage;

import com.example.aichat.ai.dto.request.AiChatRequest;
import com.example.aichat.ai.dto.response.AiTokenUsage;
import com.example.aichat.ai.entity.AiCallLog;
import com.example.aichat.ai.enums.AiCallStatus;
import com.example.aichat.ai.provider.AiModelConfigSnapshot;
import com.example.aichat.ai.service.AiCallLogService;
import org.springframework.stereotype.Component;

@Component
public class AiUsageRecorder {
    private final AiCallLogService logs;

    public AiUsageRecorder(AiCallLogService logs) {
        this.logs = logs;
    }

    public AiCallLog start(String requestId, AiChatRequest request, AiModelConfigSnapshot snapshot) {
        AiCallLog log = new AiCallLog();
        log.requestId = requestId;
        log.userId = request.userId();
        log.sessionId = request.sessionId();
        log.messageId = request.messageId();
        log.providerId = snapshot.providerId();
        log.modelId = snapshot.modelId();
        log.providerName = snapshot.providerName();
        log.modelCode = snapshot.modelCode();
        log.callType = "STREAM";
        return logs.start(log);
    }

    public void success(AiCallLog log, AiTokenUsage usage) {
        logs.finish(log.id, AiCallStatus.SUCCESS, usage, "stop", null, null);
    }

    public void failed(AiCallLog log, String code, String message) {
        logs.finish(log.id, AiCallStatus.FAILED, null, null, code, message);
    }

    public void cancelled(AiCallLog log) {
        logs.finish(log.id, AiCallStatus.CANCELLED, null, null, null, null);
    }
}
