package com.example.aichat.agent.service.impl;

import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.agent.entity.AgentDefinition;
import com.example.aichat.agent.entity.AgentKnowledgeBase;
import com.example.aichat.agent.entity.AgentTool;
import com.example.aichat.agent.mapper.AgentDefinitionMapper;
import com.example.aichat.agent.mapper.AgentKnowledgeBaseMapper;
import com.example.aichat.agent.mapper.AgentToolMapper;
import com.example.aichat.agent.model.AgentRuntimeConfig;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.service.ToolQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentRuntimeServiceImplTest {
    @Mock
    private AgentDefinitionMapper agentMapper;
    @Mock
    private AgentKnowledgeBaseMapper agentKnowledgeBaseMapper;
    @Mock
    private AgentToolMapper agentToolMapper;
    @Mock
    private AiModelService modelService;
    @Mock
    private KnowledgeBaseRepository knowledgeBaseRepository;
    @Mock
    private ToolQueryService toolQueryService;

    private AgentRuntimeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentRuntimeServiceImpl(agentMapper, agentKnowledgeBaseMapper, agentToolMapper,
                modelService, knowledgeBaseRepository, toolQueryService);
    }

    @Test
    void resolvesOwnedEnabledAgentAndRevalidatesAllBindings() {
        when(agentMapper.selectOne(any())).thenReturn(agent(100L, 7L, 1));
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of(bindingBase(11L)));
        when(knowledgeBaseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.ofNullable(null));
        when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));

        assertThatThrownBy(() -> service.resolve(7L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID);
    }

    @Test
    void resolvesAgentWhenAllResourcesRemainAvailable() {
        when(agentMapper.selectOne(any())).thenReturn(agent(100L, 7L, 1));
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of(bindingBase(11L)));
        when(knowledgeBaseRepository.findOwnedActive(7L, 11L))
                .thenReturn(Optional.of(new com.example.aichat.knowledge.model.KnowledgeBaseRecord(
                        11L, 7L, "base", null, 3L, 1, null, null, null, 1L,
                        com.example.aichat.knowledge.enums.KnowledgeBaseStatus.ACTIVE, false,
                        null, null)));
        when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));
        when(toolQueryService.listAvailable()).thenReturn(List.of(availableTool("order_query")));

        AgentRuntimeConfig result = service.resolve(7L, 100L);

        assertThat(result.agentId()).isEqualTo(100L);
        assertThat(result.modelId()).isEqualTo(2L);
        assertThat(result.knowledgeBaseIds()).containsExactly(11L);
        assertThat(result.toolCodes()).containsExactly("order_query");
    }

    @Test
    void rejectsDisabledAgentBeforeReturningRuntimeConfig() {
        when(agentMapper.selectOne(any())).thenReturn(agent(100L, 7L, 0));

        assertThatThrownBy(() -> service.resolve(7L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_DISABLED);
    }

    @Test
    void rejectsAgentWhenBoundToolIsNoLongerAvailable() {
        when(agentMapper.selectOne(any())).thenReturn(agent(100L, 7L, 1));
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
        when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));
        when(toolQueryService.listAvailable()).thenReturn(List.of());

        assertThatThrownBy(() -> service.resolve(7L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_TOOL_INVALID);
    }

    private AgentDefinition agent(Long id, Long userId, int status) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(id);
        agent.setUserId(userId);
        agent.setModelId(2L);
        agent.setSystemPrompt("You are support.");
        agent.setStatus(status);
        return agent;
    }

    private AgentKnowledgeBase bindingBase(Long id) {
        AgentKnowledgeBase binding = new AgentKnowledgeBase();
        binding.setAgentId(100L);
        binding.setKnowledgeBaseId(id);
        return binding;
    }

    private AgentTool bindingTool(String code) {
        AgentTool binding = new AgentTool();
        binding.setAgentId(100L);
        binding.setToolCode(code);
        return binding;
    }

    private com.example.aichat.ai.entity.AiModel chatModel(Long id) {
        com.example.aichat.ai.entity.AiModel model = new com.example.aichat.ai.entity.AiModel();
        model.id = id;
        model.modelType = "CHAT";
        model.status = 1;
        return model;
    }

    private AvailableToolView availableTool(String code) {
        return new AvailableToolView(code, code, "description", ToolType.HTTP, null);
    }
}
