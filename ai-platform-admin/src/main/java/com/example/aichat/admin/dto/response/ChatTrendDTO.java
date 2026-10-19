package com.example.aichat.admin.dto.response;

import java.time.LocalDate;

public record ChatTrendDTO(
        LocalDate date,
        long newSessions,
        long messages,
        long userMessages,
        long assistantMessages,
        long completedMessages,
        long failedMessages,
        long generationTasks,
        long successfulGenerationTasks
) { }
