package com.example.aichat.chat.manager;

import com.example.aichat.chat.model.ChatGenerationResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ChatGenerationTaskManager {
    private final ConcurrentMap<String, ChatGenerationResult> tasks = new ConcurrentHashMap<>();

    public void register(ChatGenerationResult generation) {
        tasks.put(generation.requestId(), generation);
    }

    public void remove(String requestId) {
        tasks.remove(requestId);
    }

    public ChatGenerationResult find(String requestId) {
        return tasks.get(requestId);
    }
}
