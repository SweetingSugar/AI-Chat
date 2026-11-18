package com.example.aichat.agent.service;

import com.example.aichat.agent.model.AgentRuntimeConfig;

public interface AgentRuntimeService {
    AgentRuntimeConfig resolve(Long userId, Long agentId);
}
