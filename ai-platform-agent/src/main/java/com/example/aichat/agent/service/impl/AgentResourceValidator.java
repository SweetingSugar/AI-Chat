package com.example.aichat.agent.service.impl;

import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.enums.AiModelType;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.service.ToolQueryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class AgentResourceValidator {
    private final AiModelService modelService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ToolQueryService toolQueryService;

    public AgentResourceValidator(AiModelService modelService,
                                  KnowledgeBaseRepository knowledgeBaseRepository,
                                  ToolQueryService toolQueryService) {
        this.modelService = modelService;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.toolQueryService = toolQueryService;
    }

    public void validateModel(Long modelId) {
        AiModel model;
        try {
            model = modelService.requireEnabled(modelId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AGENT_MODEL_INVALID,
                    "Agent chat model is not available", exception);
        }
        if (model == null || !AiModelType.CHAT.name().equals(model.modelType)) {
            throw new BusinessException(ErrorCode.AGENT_MODEL_INVALID);
        }
    }

    public List<Long> validateKnowledgeBases(Long userId, List<Long> ids) {
        List<Long> normalized = normalizeIds(ids);
        if (normalized.size() > 10) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At most 10 knowledge bases can be bound");
        }
        for (Long id : normalized) {
            if (knowledgeBaseRepository.findOwnedActive(userId, id).isEmpty()) {
                throw new BusinessException(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID,
                        "Agent knowledge base is not available");
            }
        }
        return normalized;
    }

    public List<String> validateTools(List<String> codes) {
        List<String> normalized = normalizeCodes(codes);
        if (normalized.size() > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "At most 20 tools can be bound");
        }
        if (normalized.isEmpty()) {
            return normalized;
        }
        Map<String, AvailableToolView> available = toolQueryService.listAvailable().stream()
                .collect(Collectors.toMap(AvailableToolView::toolCode, Function.identity(),
                        (first, ignored) -> first));
        for (String code : normalized) {
            if (!available.containsKey(code)) {
                throw new BusinessException(ErrorCode.AGENT_TOOL_INVALID,
                        "Agent tool is not available");
            }
        }
        return normalized;
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        if (ids.stream().anyMatch(id -> id == null)) {
            throw new BusinessException(ErrorCode.AGENT_KNOWLEDGE_BASE_INVALID,
                    "Knowledge base id is required");
        }
        return List.copyOf(new LinkedHashSet<>(ids));
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String code : codes) {
            if (!StringUtils.hasText(code)) {
                throw new BusinessException(ErrorCode.AGENT_TOOL_INVALID,
                        "Tool code is required");
            }
            normalized.add(code.trim());
        }
        return List.copyOf(normalized);
    }
}
