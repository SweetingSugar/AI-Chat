package com.example.aichat.agent.service;

import com.example.aichat.agent.dto.AgentStatusRequest;
import com.example.aichat.agent.dto.AgentUpsertRequest;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.agent.vo.AgentVO;
import com.example.aichat.common.api.PageResult;

public interface AgentService {
    PageResult<AgentVO> page(Long userId, int page, int size);

    AgentVO get(Long userId, Long agentId);

    AgentVO create(Long userId, AgentUpsertRequest request);

    AgentVO update(Long userId, Long agentId, AgentUpsertRequest request);

    void delete(Long userId, Long agentId);

    AgentVO updateStatus(Long userId, Long agentId, AgentStatusRequest request);

    PageResult<AgentAdminVO> adminPage(int page, int size);

    AgentAdminVO adminGet(Long agentId);
}
