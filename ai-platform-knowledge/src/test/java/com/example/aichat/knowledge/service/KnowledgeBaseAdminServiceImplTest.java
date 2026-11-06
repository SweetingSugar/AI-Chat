package com.example.aichat.knowledge.service;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.model.KnowledgeBaseRecord;
import com.example.aichat.knowledge.model.KnowledgeDocumentRecord;
import com.example.aichat.knowledge.repository.KnowledgeBaseRepository;
import com.example.aichat.knowledge.repository.KnowledgeDocumentRepository;
import com.example.aichat.knowledge.service.impl.KnowledgeBaseAdminServiceImpl;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseAdminServiceImplTest {
    @Mock
    private KnowledgeBaseRepository baseRepository;
    @Mock
    private KnowledgeDocumentRepository documentRepository;

    private KnowledgeBaseAdminService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseAdminServiceImpl(baseRepository, documentRepository);
    }

    @Test
    void pagesAllBasesWithNormalizedPagination() {
        KnowledgeBaseRecord record = base(12L, 100L, false);
        when(baseRepository.countAll()).thenReturn(1L);
        when(baseRepository.findPageAll(1, 100)).thenReturn(List.of(record));

        PageResult<KnowledgeBaseAdminVO> result = service.page(0, 101);

        assertThat(result.records()).singleElement().satisfies(base -> {
            assertThat(base.id()).isEqualTo(12L);
            assertThat(base.userId()).isEqualTo(100L);
            assertThat(base.status()).isEqualTo(KnowledgeBaseStatus.ACTIVE);
        });
        assertThat(result.current()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.pages()).isEqualTo(1);
        verify(baseRepository).findPageAll(1, 100);
    }

    @Test
    void rejectsDocumentPageWhenBaseIsMissingOrDeleted() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.documentPage(12L, 1, 20));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_BASE_NOT_FOUND");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void rejectsDocumentContentFromAnotherKnowledgeBase() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 11L, KnowledgeDocumentSourceType.TEXT, "内容")));

        Throwable thrown = catchThrowable(() -> service.documentContent(12L, 21L));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_DOCUMENT_NOT_FOUND");
    }

    @Test
    void returnsTextContent() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 12L, KnowledgeDocumentSourceType.TEXT, "原始内容")));

        KnowledgeDocumentContentVO result = service.documentContent(12L, 21L);

        assertThat(result.documentId()).isEqualTo(21L);
        assertThat(result.title()).isEqualTo("文档");
        assertThat(result.content()).isEqualTo("原始内容");
    }

    @Test
    void rejectsFileContent() {
        when(baseRepository.findNotDeletedById(12L)).thenReturn(Optional.of(base(12L, 100L, false)));
        when(documentRepository.findById(21L))
                .thenReturn(Optional.of(document(21L, 12L, KnowledgeDocumentSourceType.FILE, null)));

        Throwable thrown = catchThrowable(() -> service.documentContent(12L, 21L));
        assertThat(thrown).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) thrown).errorCode().code())
                .isEqualTo("KNOWLEDGE_DOCUMENT_CONTENT_NOT_SUPPORTED");
    }

    private KnowledgeBaseRecord base(Long id, Long userId, boolean deleted) {
        return new KnowledgeBaseRecord(id, userId, "知识库", null, 30L, 1,
                null, null, null, 1L, KnowledgeBaseStatus.ACTIVE, deleted,
                LocalDateTime.now(), LocalDateTime.now());
    }

    private KnowledgeDocumentRecord document(Long id, Long baseId,
                                             KnowledgeDocumentSourceType sourceType,
                                             String rawContent) {
        return new KnowledgeDocumentRecord(id, baseId, sourceType,
                sourceType == KnowledgeDocumentSourceType.FILE ? 31L : null,
                "文档", rawContent, null, KnowledgeDocumentStatus.READY,
                1L, 1L, 1, null, null, false,
                LocalDateTime.now(), LocalDateTime.now());
    }
}
