package com.example.aichat.knowledge.service;

import com.example.aichat.ai.api.AiModelUsageValidator;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeModelUsageValidator implements AiModelUsageValidator {
    private final KnowledgeBaseRepository baseRepository;

    public KnowledgeModelUsageValidator(KnowledgeBaseRepository baseRepository) {
        this.baseRepository = baseRepository;
    }

    @Override
    public void assertCanDelete(Long modelId) {
        if (baseRepository.countByEmbeddingModelId(modelId) > 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_MODEL_INVALID,
                    "Embedding model is referenced by a knowledge base");
        }
    }

    @Override
    public void assertCanChangeEmbeddingDefinition(Long modelId, Integer newDimension) {
        if (baseRepository.countByEmbeddingModelId(modelId) > 0) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_REINDEXING,
                    "Embedding model definition cannot change while referenced by a knowledge base");
        }
    }
}
