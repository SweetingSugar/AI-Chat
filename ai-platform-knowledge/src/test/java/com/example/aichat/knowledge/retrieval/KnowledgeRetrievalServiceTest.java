package com.example.aichat.knowledge.retrieval;

import com.example.aichat.ai.api.AiEmbeddingService;
import com.example.aichat.ai.entity.AiModel;
import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkSearchResult;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeChunkEmbeddingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeRetrievalServiceTest {
    @Mock
    private KnowledgeBaseRepository baseRepository;
    @Mock
    private KnowledgeChunkEmbeddingRepository embeddingRepository;
    @Mock
    private AiModelService modelService;
    @Mock
    private AiEmbeddingService embeddingService;

    private KnowledgeRetrievalService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeRetrievalServiceImpl(baseRepository, embeddingRepository,
                modelService, embeddingService, 2);
    }

    @Test
    void returnsEmptyContextWithoutSelectedKnowledgeBases() {
        KnowledgeContext context = service.retrieve(100L, List.of(), "question");

        assertTrue(context.sources().isEmpty());
        verifyNoInteractions(baseRepository, embeddingRepository, modelService, embeddingService);
    }

    @Test
    void embedsOncePerModelAndUsesConfiguredTopK() {
        KnowledgeBaseRecord base = base(1L, 10L);
        AiModel model = embeddingModel(10L, 3);
        when(baseRepository.findOwnedActive(100L, 1L)).thenReturn(Optional.of(base));
        when(modelService.requireEnabledEmbedding(10L)).thenReturn(model);
        when(embeddingService.embed(10L, List.of("question")))
                .thenReturn(List.of(new float[]{1, 0, 0}));
        when(embeddingRepository.search(1L, 10L, 3, 1L, new float[]{1, 0, 0}, 2))
                .thenReturn(List.of(
                        new KnowledgeChunkSearchResult(11L, 21L, "doc", "first", 0.1),
                        new KnowledgeChunkSearchResult(12L, 21L, "doc", "second", 0.2),
                        new KnowledgeChunkSearchResult(13L, 21L, "doc", "third", 0.3)));

        KnowledgeContext context = service.retrieve(100L, List.of(1L), "question");

        verify(embeddingService).embed(10L, List.of("question"));
        verify(embeddingRepository).search(1L, 10L, 3, 1L, new float[]{1, 0, 0}, 2);
        assertEquals(2, context.sources().size());
        assertTrue(context.prompt().contains("untrusted knowledge sources"));
    }

    private KnowledgeBaseRecord base(Long id, Long modelId) {
        return new KnowledgeBaseRecord(id, 100L, "base", null, modelId, 1,
                null, null, null, 1L, KnowledgeBaseStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private AiModel embeddingModel(Long id, int dimension) {
        AiModel model = new AiModel();
        model.id = id;
        model.embeddingDimension = dimension;
        return model;
    }
}
