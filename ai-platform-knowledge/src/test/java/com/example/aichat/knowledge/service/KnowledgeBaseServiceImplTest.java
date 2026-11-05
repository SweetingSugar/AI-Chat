package com.example.aichat.knowledge.service;

import com.example.aichat.ai.service.AiModelService;
import com.example.aichat.file.service.FileService;
import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.repository.KnowledgeIndexTaskRepository;
import com.example.aichat.knowledge.service.impl.KnowledgeBaseServiceImpl;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {
    @Mock
    private KnowledgeBaseRepository baseRepository;
    @Mock
    private KnowledgeDocumentRepository documentRepository;
    @Mock
    private KnowledgeIndexTaskRepository taskRepository;
    @Mock
    private AiModelService modelService;
    @Mock
    private FileService fileService;

    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseServiceImpl(baseRepository, documentRepository, taskRepository,
                modelService, fileService, 5);
    }

    @Test
    void rejectsDocumentOperationWhenPathKnowledgeBaseDoesNotOwnDocument() {
        KnowledgeDocumentRecord document = document(21L, 11L);
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document));
        when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.deleteDocument(100L, 12L, 21L));

        assertEquals("KNOWLEDGE_DOCUMENT_NOT_FOUND", exception.errorCode().code());
        verify(documentRepository, never()).markDeleted(21L);
    }

    @Test
    void returnsOriginalTextDocumentContent() {
        when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
                21L, 12L, KnowledgeDocumentSourceType.TEXT, null, " 第一条\n\n第二条 ")));

        KnowledgeDocumentContentVO result = service.documentContent(100L, 12L, 21L);

        assertEquals(21L, result.documentId());
        assertEquals("title", result.title());
        assertEquals(" 第一条\n\n第二条 ", result.content());
    }

    @Test
    void rejectsContentWhenPathKnowledgeBaseDoesNotOwnDocument() {
        when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document(21L, 11L)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.documentContent(100L, 12L, 21L));

        assertEquals("KNOWLEDGE_DOCUMENT_NOT_FOUND", exception.errorCode().code());
    }

    @Test
    void rejectsContentForNonTextDocument() {
        when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
                21L, 12L, KnowledgeDocumentSourceType.FILE, 31L, null)));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.documentContent(100L, 12L, 21L));

        assertEquals("KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED", exception.errorCode().code());
    }

    @Test
    void rejectsEmptyTextDocumentContent() {
        when(baseRepository.findOwnedActive(100L, 12L)).thenReturn(Optional.of(base(12L)));
        when(documentRepository.findById(21L)).thenReturn(Optional.of(document(
                21L, 12L, KnowledgeDocumentSourceType.TEXT, null, "  ")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.documentContent(100L, 12L, 21L));

        assertEquals("KNOWLEDGE_DOCUMENT_PARSE_FAILED", exception.errorCode().code());
    }

    private KnowledgeBaseRecord base(Long id) {
        return new KnowledgeBaseRecord(id, 100L, "base", null, 30L, 1,
                null, null, null, 1L, KnowledgeBaseStatus.ACTIVE, false,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeDocumentRecord document(Long id, Long baseId) {
        return document(id, baseId, KnowledgeDocumentSourceType.TEXT, null, "content");
    }

    private KnowledgeDocumentRecord document(Long id, Long baseId,
                                             KnowledgeDocumentSourceType sourceType,
                                             Long fileId,
                                             String rawContent) {
        return new KnowledgeDocumentRecord(id, baseId, sourceType, fileId,
                "title", rawContent, null, KnowledgeDocumentStatus.READY, 1L, 1L,
                1, null, null, false, LocalDateTime.now(), LocalDateTime.now());
    }
}
