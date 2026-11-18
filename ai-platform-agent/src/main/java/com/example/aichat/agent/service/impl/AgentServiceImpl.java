package com.example.aichat.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aichat.agent.dto.AgentStatusRequest;
import com.example.aichat.agent.dto.AgentUpsertRequest;
import com.example.aichat.agent.entity.AgentDefinition;
import com.example.aichat.agent.entity.AgentKnowledgeBase;
import com.example.aichat.agent.entity.AgentTool;
import com.example.aichat.agent.mapper.AgentDefinitionMapper;
import com.example.aichat.agent.mapper.AgentKnowledgeBaseMapper;
import com.example.aichat.agent.mapper.AgentToolMapper;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentVO;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.tool.service.ToolQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class AgentServiceImpl implements AgentService {
    private final AgentDefinitionMapper agentMapper;
    private final AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;
    private final AgentToolMapper agentToolMapper;
    private final AgentResourceValidator resourceValidator;

    @Autowired
    public AgentServiceImpl(AgentDefinitionMapper agentMapper,
                            AgentKnowledgeBaseMapper agentKnowledgeBaseMapper,
                            AgentToolMapper agentToolMapper,
                            AgentResourceValidator resourceValidator) {
        this.agentMapper = agentMapper;
        this.agentKnowledgeBaseMapper = agentKnowledgeBaseMapper;
        this.agentToolMapper = agentToolMapper;
        this.resourceValidator = resourceValidator;
    }

    public AgentServiceImpl(AgentDefinitionMapper agentMapper,
                            AgentKnowledgeBaseMapper agentKnowledgeBaseMapper,
                            AgentToolMapper agentToolMapper,
                            AiModelService modelService,
                            KnowledgeBaseRepository knowledgeBaseRepository,
                            ToolQueryService toolQueryService) {
        this(agentMapper, agentKnowledgeBaseMapper, agentToolMapper,
                new AgentResourceValidator(modelService, knowledgeBaseRepository, toolQueryService));
    }

    @Override
    public PageResult<AgentVO> page(Long userId, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), 100);
        List<AgentDefinition> all = agentMapper.selectList(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getUserId, userId)
                .orderByDesc(AgentDefinition::getUpdateTime)
                .orderByDesc(AgentDefinition::getId));
        long total = agentMapper.selectCount(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getUserId, userId));
        int from = Math.min((current - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        List<AgentVO> records = all.subList(from, to).stream().map(this::toView).toList();
        return new PageResult<>(records, total, current, pageSize,
                total == 0 ? 0 : (total + pageSize - 1) / pageSize);
    }

    @Override
    public AgentVO get(Long userId, Long agentId) {
        return toView(requireOwned(userId, agentId));
    }

    @Override
    @Transactional
    public AgentVO create(Long userId, AgentUpsertRequest request) {
        NormalizedRequest normalized = validate(userId, request, null);
        AgentDefinition agent = new AgentDefinition();
        agent.setUserId(userId);
        copy(normalized, agent);
        agent.setStatus(1);
        agent.setDeleted(0);
        agentMapper.insert(agent);
        replaceBindings(agent.getId(), normalized.knowledgeBaseIds(), normalized.toolCodes());
        return toView(agent);
    }

    @Override
    @Transactional
    public AgentVO update(Long userId, Long agentId, AgentUpsertRequest request) {
        AgentDefinition agent = requireOwned(userId, agentId);
        NormalizedRequest normalized = validate(userId, request, agentId);
        copy(normalized, agent);
        agentMapper.updateById(agent);
        replaceBindings(agentId, normalized.knowledgeBaseIds(), normalized.toolCodes());
        return toView(agent);
    }

    @Override
    @Transactional
    public void delete(Long userId, Long agentId) {
        requireOwned(userId, agentId);
        if (agentMapper.deleteById(agentId) == 0) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        deleteBindings(agentId);
    }

    @Override
    @Transactional
    public AgentVO updateStatus(Long userId, Long agentId, AgentStatusRequest request) {
        AgentDefinition agent = requireOwned(userId, agentId);
        agent.setStatus(request.status());
        agentMapper.updateById(agent);
        return toView(agent);
    }

    private NormalizedRequest validate(Long userId, AgentUpsertRequest request, Long excludedId) {
        String name = request.name() == null ? null : request.name().trim();
        String systemPrompt = request.systemPrompt() == null ? null : request.systemPrompt().trim();
        if (!StringUtils.hasText(name) || !StringUtils.hasText(systemPrompt)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Agent name and system prompt are required");
        }
        assertNameAvailable(userId, name, excludedId);
        resourceValidator.validateModel(request.modelId());
        List<Long> knowledgeBaseIds = resourceValidator.validateKnowledgeBases(userId,
                request.knowledgeBaseIds());
        List<String> toolCodes = resourceValidator.validateTools(request.toolCodes());
        String description = StringUtils.hasText(request.description())
                ? request.description().trim() : null;
        return new NormalizedRequest(name, description, systemPrompt, request.modelId(),
                knowledgeBaseIds, toolCodes);
    }

    private void assertNameAvailable(Long userId, String name, Long excludedId) {
        LambdaQueryWrapper<AgentDefinition> query = new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getUserId, userId)
                .eq(AgentDefinition::getName, name);
        if (excludedId != null) {
            query.ne(AgentDefinition::getId, excludedId);
        }
        if (agentMapper.selectCount(query) > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "Agent name already exists");
        }
    }

    private void copy(NormalizedRequest normalized, AgentDefinition agent) {
        agent.setName(normalized.name());
        agent.setDescription(normalized.description());
        agent.setSystemPrompt(normalized.systemPrompt());
        agent.setModelId(normalized.modelId());
    }

    private void replaceBindings(Long agentId, List<Long> knowledgeBaseIds, List<String> toolCodes) {
        deleteBindings(agentId);
        for (Long knowledgeBaseId : knowledgeBaseIds) {
            AgentKnowledgeBase binding = new AgentKnowledgeBase();
            binding.setAgentId(agentId);
            binding.setKnowledgeBaseId(knowledgeBaseId);
            agentKnowledgeBaseMapper.insert(binding);
        }
        for (String toolCode : toolCodes) {
            AgentTool binding = new AgentTool();
            binding.setAgentId(agentId);
            binding.setToolCode(toolCode);
            agentToolMapper.insert(binding);
        }
    }

    private void deleteBindings(Long agentId) {
        agentKnowledgeBaseMapper.delete(new LambdaQueryWrapper<AgentKnowledgeBase>()
                .eq(AgentKnowledgeBase::getAgentId, agentId));
        agentToolMapper.delete(new LambdaQueryWrapper<AgentTool>()
                .eq(AgentTool::getAgentId, agentId));
    }

    private AgentDefinition requireOwned(Long userId, Long agentId) {
        AgentDefinition agent = agentMapper.selectOne(new LambdaQueryWrapper<AgentDefinition>()
                .eq(AgentDefinition::getId, agentId)
                .eq(AgentDefinition::getUserId, userId));
        if (agent == null) {
            throw new BusinessException(ErrorCode.AGENT_NOT_FOUND);
        }
        return agent;
    }

    private AgentVO toView(AgentDefinition agent) {
        List<AgentKnowledgeBase> knowledgeBindings = agentKnowledgeBaseMapper.selectList(
                new LambdaQueryWrapper<AgentKnowledgeBase>()
                        .eq(AgentKnowledgeBase::getAgentId, agent.getId())
                        .orderByAsc(AgentKnowledgeBase::getId));
        List<AgentTool> toolBindings = agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentTool>()
                        .eq(AgentTool::getAgentId, agent.getId())
                        .orderByAsc(AgentTool::getId));
        return new AgentVO(agent.getId(), agent.getName(), agent.getDescription(), agent.getSystemPrompt(),
                agent.getModelId(), knowledgeBindings.stream().map(AgentKnowledgeBase::getKnowledgeBaseId).toList(),
                toolBindings.stream().map(AgentTool::getToolCode).toList(), agent.getStatus(),
                agent.getCreateTime(), agent.getUpdateTime());
    }

    private record NormalizedRequest(String name, String description, String systemPrompt, Long modelId,
                                     List<Long> knowledgeBaseIds, List<String> toolCodes) {
    }
}
