package com.example.aichat.chat.controller;

import com.example.aichat.chat.dto.CreateSessionDTO;
import com.example.aichat.chat.dto.UpdateSessionDTO;
import com.example.aichat.chat.service.ChatSessionService;
import com.example.aichat.chat.vo.ChatSessionDetailVO;
import com.example.aichat.chat.vo.ChatSessionVO;
import com.example.aichat.common.api.ApiResponse;
import com.example.aichat.common.api.PageResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat/sessions")
public class ChatSessionController {
    private final ChatSessionService sessionService;

    public ChatSessionController(ChatSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ApiResponse<ChatSessionVO> create(@Valid @RequestBody CreateSessionDTO request) {
        return ApiResponse.success(sessionService.create(request));
    }

    @GetMapping
    public ApiResponse<PageResult<ChatSessionVO>> page(@RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(sessionService.page(Math.max(page, 1), Math.min(Math.max(size, 1), 100)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ChatSessionDetailVO> detail(@PathVariable("id") Long id) {
        return ApiResponse.success(sessionService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ChatSessionVO> update(@PathVariable("id") Long id,
                                             @Valid @RequestBody UpdateSessionDTO request) {
        return ApiResponse.success(sessionService.update(id, request));
    }

    @PutMapping("/{id}/pin")
    public ApiResponse<ChatSessionVO> pin(@PathVariable("id") Long id,
                                          @RequestParam boolean pinned) {
        return ApiResponse.success(sessionService.pin(id, pinned));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        sessionService.delete(id);
        return ApiResponse.success(null);
    }
}
