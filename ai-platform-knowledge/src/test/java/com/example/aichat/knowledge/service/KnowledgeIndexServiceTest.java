package com.example.aichat.knowledge.service;

import com.example.aichat.ai.api.AiEmbeddingService;
import com.example.aichat.knowledge.content.KnowledgeDocumentContentReader;
import com.example.aichat.knowledge.content.TextChunk;
import com.example.aichat.knowledge.content.TextChunker;
import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.enums.KnowledgeIndexTaskStatus;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeChunkRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.model.KnowledgeIndexTaskRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeChunkEmbeddingRepository;
import com.example.aichat.knowledge.repository.KnowledgeChunkRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.impl.KnowledgeIndexServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexServiceTest {
    @Mock
    private KnowledgeDocumentContentReader contentReader;
    @Mock
    private TextChunker chunker;
    @Mock
    private AiEmbeddingService embeddingService;
    @Mock
    private KnowledgeBaseRepository baseRepository;
    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeChunkRepository chunkRepository;
    @Mock
    private KnowledgeChunkEmbeddingRepository embeddingRepository;
    @Mock
    private KnowledgeIndexTaskRepository taskRepository;

    private KnowledgeIndexService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeIndexServiceImpl(contentReader, chunker, embeddingService,
                baseRepository, documentRepository, chunkRepository, embeddingRepository, taskRepository);
    }

    @Test
    void marksDocumentReadyOnlyAfterAllEmbeddingsAreStored() {
        KnowledgeIndexTaskRecord task = task(11L, 21L, 30L, 1L);
        KnowledgeDocumentRecord document = document(21L, 11L, null);
        KnowledgeBaseRecord base = base(11L);
        TextChunk chunk = new TextChunk(0, "alpha beta", 3, Map.of());
        when(contentReader.read(21L)).thenReturn("alpha beta");
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(baseRepository.findById(11L)).thenReturn(Optional.of(base));
        when(chunker.split("alpha beta")).thenReturn(List.of(chunk));
        when(chunkRepository.findByDocumentIdAndIndexVersion(21L, 1L)).thenReturn(List.of());
        when(chunkRepository.insertAll(anyList())).thenReturn(List.of(
                new KnowledgeChunkRecord(31L, 21L, 0, "alpha beta", 3, Map.of())));
        when(embeddingService.embed(30L, List.of("alpha beta")))
                .thenReturn(List.of(new float[]{1, 0, 0}));

        service.index(task);

        verify(chunkRepository).insertAll(anyList());
        verify(embeddingRepository).insertAll(anyList());
        verify(documentRepository).markReady(21L, 1L, 1);
        verify(taskRepository).markSuccess(11L);
    }

    @Test
    void marksTaskFailedWhenEmbeddingThrows() {
        KnowledgeIndexTaskRecord task = task(11L, 21L, 30L, 1L);
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document(21L, 11L, null)));
        when(baseRepository.findById(11L)).thenReturn(Optional.of(base(11L)));
        when(contentReader.read(21L)).thenReturn("alpha beta");
        when(chunker.split("alpha beta")).thenReturn(List.of(new TextChunk(0, "alpha beta", 3, Map.of())));
        when(chunkRepository.findByDocumentIdAndIndexVersion(21L, 1L)).thenReturn(List.of());
        when(chunkRepository.insertAll(anyList())).thenReturn(List.of(
                new KnowledgeChunkRecord(31L, 21L, 0, "alpha beta", 3, Map.of())));
        when(embeddingService.embed(anyLong(), anyList()))
                .thenThrow(new com.example.aichat.common.error.BusinessException(
                        com.example.aichat.common.error.ErrorCode.KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE));

        service.index(task);

        verify(taskRepository).markFailed(eq(11L), eq("KNOWLEDGE_EMBEDDING_MODEL_UNAVAILABLE"), anyString());
        verify(documentRepository, never()).markReady(anyLong(), anyLong(), any(Integer.class));
    }

    private KnowledgeIndexTaskRecord task(Long id, Long documentId, Long modelId, Long version) {
        return new KnowledgeIndexTaskRecord(id, 11L, documentId, modelId, 1, version,
                KnowledgeIndexTaskStatus.RUNNING, 1, 3, LocalDateTime.now(), null,
                LocalDateTime.now(), null, null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeBaseRecord base(Long id) {
        return new KnowledgeBaseRecord(id, 100L, "base", null, 30L, 1,
                null, null, null, 1L, KnowledgeBaseStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeDocumentRecord document(Long id, Long baseId, Long activeVersion) {
        return new KnowledgeDocumentRecord(id, baseId, KnowledgeDocumentSourceType.TEXT, null,
                "title", "alpha beta", null, activeVersion == null ? KnowledgeDocumentStatus.PENDING : KnowledgeDocumentStatus.READY,
                1L, activeVersion, 0, null, null, false, LocalDateTime.now(), LocalDateTime.now());
    }
}
