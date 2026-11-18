package com.example.aichat.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.agent.entity.AgentDefinition;
import com.example.aichat.agent.entity.AgentKnowledgeBase;
import com.example.aichat.agent.entity.AgentTool;
import com.example.aichat.agent.mapper.AgentDefinitionMapper;
import com.example.aichat.agent.mapper.AgentKnowledgeBaseMapper;
import com.example.aichat.agent.mapper.AgentToolMapper;
import com.example.aichat.agent.model.AgentRuntimeConfig;
import com.example.aichat.agent.service.AgentRuntimeService;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.tool.service.ToolQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentRuntimeServiceImpl implements AgentRuntimeService {
    private final AgentDefinitionMapper agentMapper;
    private final AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;
    private final AgentToolMapper agentToolMapper;
    private final AgentResourceValidator resourceValidator;

    @Autowired
    public AgentRuntimeServiceImpl(AgentDefinitionMapper agentMapper,
                                   AgentKnowledgeBaseMapper agentKnowledgeBaseMapper,
                                   AgentToolMapper agentToolMapper,
                                   AgentResourceValidator resourceValidator) {
        this.agentMapper = agentMapper;
        this.agentKnowledgeBaseMapper = agentKnowledgeBaseMapper;
        this.agentToolMapper = agentToolMapper;
        this.resourceValidator = resourceValidator;
    }

    public AgentRuntimeServiceImpl(AgentDefinitionMapper agentMapper,
                                   AgentKnowledgeBaseMapper agentKnowledgeBaseMapper,
                                   AgentToolMapper agentToolMapper,
                                   AiModelService modelService,
                                   KnowledgeBaseRepository knowledgeBaseRepository,
                                   ToolQueryService toolQueryService) {
        this(agentMapper, agentKnowledgeBaseMapper, agentToolMapper,
                new AgentResourceValidator(modelService, knowledgeBaseRepository, toolQueryService));
    }

    @Override
    public AgentRuntimeConfig resolve(Long userId, Long agentId) {
        AgentDefinition agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getId, agentId)
                .eq(AgentDefinition::getUserId, userId));
        if (agent == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        if (!Integer.valueOf(1).equals(agent.getStatus())) {
            throw new BusinessException(ErrorCode.AGENT_DISABLED);
        }

        resourceValidator.validateModel(agent.getModelId());
        List<Long> knowledgeBaseIds = agentKnowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<AgentKnowledgeBase>()
                                .eq(AgentKnowledgeBase::getAgentId, agentId)
                                .orderByAsc(AgentKnowledgeBase::getId))
                .stream().map(AgentKnowledgeBase::getKnowledgeBaseId).toList();
        List<String> toolCodes = agentToolMapper.selectList(
                        new LambdaQueryWrapper<AgentTool>()
                                .eq(AgentTool::getAgentId, agentId)
                                .orderByAsc(AgentTool::getId))
                .stream().map(AgentTool::getToolCode).toList();
        resourceValidator.validateKnowledgeBases(userId, knowledgeBaseIds);
        resourceValidator.validateTools(toolCodes);
        return new AgentRuntimeConfig(agent.getId(), agent.getModelId(), agent.getSystemPrompt(),
                knowledgeBaseIds, toolCodes);
    }
}
