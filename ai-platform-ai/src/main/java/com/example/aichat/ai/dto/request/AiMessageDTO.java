package com.example.aichat.ai.dto.request;

import com.example.aichat.ai.enums.AiMessageRole;

public record AiMessageDTO(AiMessageRole role, String content) { }
