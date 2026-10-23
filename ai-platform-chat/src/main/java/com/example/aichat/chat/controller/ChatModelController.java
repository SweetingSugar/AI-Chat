package com.example.aichat.chat.controller;

import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.dto.response.AiModelDTO;
import com.example.aichat.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat/models")
public class ChatModelController {
    private final AiModelQueryService modelQueryService;

    public ChatModelController(AiModelQueryService modelQueryService) {
        this.modelQueryService = modelQueryService;
    }

    @GetMapping
    public ApiResponse<List<AiModelDTO>> list() {
        return ApiResponse.success(modelQueryService.listAvailableModels());
    }
}
