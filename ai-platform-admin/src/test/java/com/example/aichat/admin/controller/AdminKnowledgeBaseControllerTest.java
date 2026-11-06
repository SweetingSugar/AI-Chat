package com.example.aichat.admin.controller;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.knowledge.enums.KnowledgeBaseStatus;
import com.example.aichat.knowledge.enums.KnowledgeDocumentSourceType;
import com.example.aichat.knowledge.enums.KnowledgeDocumentStatus;
import com.example.aichat.knowledge.service.KnowledgeBaseAdminService;
import com.example.aichat.knowledge.vo.KnowledgeBaseAdminVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import com.example.aichat.knowledge.vo.KnowledgeDocumentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminKnowledgeBaseControllerTest {
    @Mock
    private KnowledgeBaseAdminService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminKnowledgeBaseController(service)).build();
    }

    @Test
    void pagesKnowledgeBases() throws Exception {
        when(service.page(2, 30)).thenReturn(new PageResult<>(List.of(
                new KnowledgeBaseAdminVO(12L, 100L, "产品手册", "说明", 30L, 1,
                        null, null, 1L, false, KnowledgeBaseStatus.ACTIVE,
                        LocalDateTime.of(2026, 11, 5, 10, 0),
                        LocalDateTime.of(2026, 11, 5, 10, 0))),
                1, 2, 30, 1));

        mockMvc.perform(get("/api/admin/knowledge-bases")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(12))
                .andExpect(jsonPath("$.data.records[0].userId").value(100));

        verify(service).page(2, 30);
    }

    @Test
    void pagesDocumentsForKnowledgeBase() throws Exception {
        when(service.documentPage(12L, 1, 20)).thenReturn(new PageResult<>(List.of(
                new KnowledgeDocumentVO(21L, "制度", KnowledgeDocumentSourceType.TEXT, null,
                        KnowledgeDocumentStatus.READY, 1L, 1L, 3, null, null,
                        LocalDateTime.of(2026, 11, 5, 10, 0),
                        LocalDateTime.of(2026, 11, 5, 10, 0))),
                1, 1, 20, 1));

        mockMvc.perform(get("/api/admin/knowledge-bases/12/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value(21))
                .andExpect(jsonPath("$.data.records[0].title").value("制度"));

        verify(service).documentPage(12L, 1, 20);
    }

    @Test
    void readsDocumentContent() throws Exception {
        when(service.documentContent(12L, 21L))
                .thenReturn(new KnowledgeDocumentContentVO(21L, "制度", "第一条"));

        mockMvc.perform(get("/api/admin/knowledge-bases/12/documents/21/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(21))
                .andExpect(jsonPath("$.data.content").value("第一条"));

        verify(service).documentContent(12L, 21L);
    }
}
