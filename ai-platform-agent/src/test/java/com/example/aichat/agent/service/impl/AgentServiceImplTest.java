package com.example.aichat.agent.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.service.ToolQueryService;
import com.example.aichat.agent.dto.AgentUpsertRequest;
import com.example.aichat.agent.entity.AgentDefinition;
import com.example.aichat.agent.entity.AgentKnowledgeBase;
import com.example.aichat.agent.entity.AgentTool;
import com.example.aichat.agent.mapper.AgentDefinitionMapper;
import com.example.aichat.agent.mapper.AgentKnowledgeBaseMapper;
import com.example.aichat.agent.mapper.AgentToolMapper;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.agent.vo.AgentVO;
import com.example.aichat.common.api.PageResult;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {
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

    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentServiceImpl(agentMapper, agentKnowledgeBaseMapper, agentToolMapper,
                modelService, knowledgeBaseRepository, toolQueryService);
    }

    @Test
    void createsAgentWithValidatedBindings() {
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(knowledgeBaseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.of(activeBase(11L)));
        when(toolQueryService.listAvailable()).thenReturn(List.of(availableTool("order_query")));
        when(agentMapper.selectCount(any())).thenReturn(0L);
        when(agentMapper.insert(any(AgentDefinition.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, AgentDefinition.class).setId(100L);
            return 1;
        });
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of(bindingBase(11L)));
        when(agentToolMapper.selectList(any())).thenReturn(List.of(bindingTool("order_query")));

        AgentVO result = service.create(7L, request(2L, List.of(11L), List.of("order_query")));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.knowledgeBaseIds()).containsExactly(11L);
        assertThat(result.toolCodes()).containsExactly("order_query");
        verify(agentKnowledgeBaseMapper).insert(any(AgentKnowledgeBase.class));
        verify(agentToolMapper).insert(any(AgentTool.class));
    }

    @Test
    void rejectsBindingKnowledgeBaseOwnedByAnotherUser() {
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(knowledgeBaseRepository.findOwnedActive(7L, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(7L, request(2L, List.of(11L), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID);
        verify(agentMapper, never()).insert(any(AgentDefinition.class));
    }

    @Test
    void rejectsUnavailableTool() {
        when(modelService.requireEnabled(2L)).thenReturn(chatModel(2L));
        when(toolQueryService.listAvailable()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(7L, request(2L, List.of(), List.of("missing"))))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_TOOL_INVALID);
        verify(agentMapper, never()).insert(any(AgentDefinition.class));
    }

    @Test
    void rejectsEmbeddingModel() {
        AiModel model = chatModel(2L);
        model.modelType = "EMBEDDING";
        when(modelService.requireEnabled(2L)).thenReturn(model);

        assertThatThrownBy(() -> service.create(7L, request(2L, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_MODEL_INVALID);
    }

    @Test
    void updateValidatesEverythingBeforeDeletingOldBindings() {
        when(agentMapper.selectOne(any())).thenReturn(existingAgent(100L, 7L));
        when(modelService.requireEnabled(3L))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> service.update(7L, 100L, request(3L, List.of(), List.of())))
                .isInstanceOf(BusinessException.class);
        verify(agentKnowledgeBaseMapper, never()).delete(any());
        verify(agentToolMapper, never()).delete(any());
    }

    @Test
    void rejectsDuplicateNameForSameUser() {
        when(agentMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(7L, request(2L, List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
    }

    @Test
    void adminPageReturnsAgentsAcrossUsersWithAdminView() {
        AgentDefinition newest = existingAgent(101L, 7L);
        newest.setName("Newest");
        newest.setUpdateTime(LocalDateTime.of(2026, 11, 20, 12, 0));
        AgentDefinition older = existingAgent(100L, 8L);
        older.setName("Older");
        older.setUpdateTime(LocalDateTime.of(2026, 11, 19, 12, 0));
        Page<AgentDefinition> page = new Page<>(1, 20);
        page.setRecords(List.of(newest, older));
        page.setTotal(2L);
        when(agentMapper.selectPage(any(), any())).thenReturn(page);
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
        when(agentToolMapper.selectList(any())).thenReturn(List.of());

        PageResult<AgentAdminVO> result = service.adminPage(1, 20);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).extracting(AgentAdminVO::id).containsExactly(101L, 100L);
        assertThat(result.records()).extracting(AgentAdminVO::userId).containsExactly(7L, 8L);
        verifyNoInteractions(modelService, knowledgeBaseRepository, toolQueryService);
    }

    @Test
    void adminPageNormalizesPageAndSize() {
        Page<AgentDefinition> page = new Page<>(1, 100);
        page.setRecords(List.of(existingAgent(101L, 7L)));
        page.setTotal(1L);
        when(agentMapper.selectPage(any(), any())).thenReturn(page);
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of());
        when(agentToolMapper.selectList(any())).thenReturn(List.of());

        PageResult<AgentAdminVO> result = service.adminPage(0, 1000);

        assertThat(result.current()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.pages()).isEqualTo(1);
    }

    @Test
    void adminGetReturnsBindingsAndDoesNotValidateResources() {
        AgentDefinition agent = existingAgent(100L, 8L);
        when(agentMapper.selectById(100L)).thenReturn(agent);
        AgentKnowledgeBase baseBinding = bindingBase(11L);
        baseBinding.setId(1L);
        AgentTool toolBinding = bindingTool("order_query");
        toolBinding.setId(2L);
        when(agentKnowledgeBaseMapper.selectList(any())).thenReturn(List.of(baseBinding));
        when(agentToolMapper.selectList(any())).thenReturn(List.of(toolBinding));

        AgentAdminVO result = service.adminGet(100L);

        assertThat(result.userId()).isEqualTo(8L);
        assertThat(result.knowledgeBaseIds()).containsExactly(11L);
        assertThat(result.toolCodes()).containsExactly("order_query");
        verifyNoInteractions(modelService, knowledgeBaseRepository, toolQueryService);
    }

    @Test
    void adminGetTreatsMissingAgentAsNotFound() {
        when(agentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.adminGet(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.AGENT_NOT_FOUND);
        verify(agentKnowledgeBaseMapper, never()).selectList(any());
        verify(agentToolMapper, never()).selectList(any());
    }

    private AgentUpsertRequest request(Long modelId, List<Long> knowledgeBaseIds, List<String> toolCodes) {
        return new AgentUpsertRequest("Support", "Support agent", "You are support.", modelId,
                knowledgeBaseIds, toolCodes);
    }

    private AiModel chatModel(Long id) {
        AiModel model = new AiModel();
        model.id = id;
        model.modelType = "CHAT";
        model.status = 1;
        return model;
    }

    private KnowledgeBaseRecord activeBase(Long id) {
        return new KnowledgeBaseRecord(id, 7L, "Base", null, 3L, 1,
                null, null, null, 1L,
                com.example.aichat.knowledge.enums.KnowledgeBaseStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private AvailableToolView availableTool(String code) {
        return new AvailableToolView(code, code, "description", ToolType.HTTP,
                JsonNodeFactory.instance.objectNode());
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

    private AgentDefinition existingAgent(Long id, Long userId) {
        AgentDefinition agent = new AgentDefinition();
        agent.setId(id);
        agent.setUserId(userId);
        agent.setName("Support");
        agent.setDescription("Support agent");
        agent.setSystemPrompt("You are support.");
        agent.setModelId(2L);
        agent.setStatus(1);
        agent.setDeleted(0);
        return agent;
    }
}
