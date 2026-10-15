package com.example.aichat.chat.controller;

import com.example.aichat.chat.dto.ChatMessageQueryDTO;
import com.example.aichat.chat.service.ChatMessageService;
import com.example.aichat.chat.vo.ChatMessageVO;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/sessions/{sessionId}/messages")
public class ChatMessageController {
    private final ChatMessageService messageService;

    public ChatMessageController(ChatMessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResponse<PageResult<ChatMessageVO>> page(@PathVariable Long sessionId,
                                                       @RequestParam(name = "page", defaultValue = "1") Integer page,
                                                       @RequestParam(name = "size", defaultValue = "20") Integer size,
                                                       @RequestParam(name = "status", required = false) String status) {
        return ApiResponse.success(messageService.page(sessionId, new ChatMessageQueryDTO(page, size, status)));
    }
}
