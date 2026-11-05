package com.example.aichat.knowledge.controller;

import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.example.aichat.knowledge.service.KnowledgeBaseService;
import com.example.aichat.knowledge.vo.KnowledgeDocumentContentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseControllerTest {
    @Mock
    private KnowledgeBaseService service;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new KnowledgeBaseController(service, currentUserProvider)).build();
        doReturn(new CurrentUser(100L, "alice")).when(currentUserProvider).requireCurrentUser();
    }

    @Test
    void readsTextDocumentContent() throws Exception {
        when(service.documentContent(100L, 12L, 21L)).thenReturn(
                new KnowledgeDocumentContentVO(21L, "请假制度", "第一条……"));

        mockMvc.perform(get("/api/knowledge-bases/12/documents/21/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.data.documentId").value(21))
                .andExpect(jsonPath("$.data.title").value("请假制度"))
                .andExpect(jsonPath("$.data.content").value("第一条……"));

        verify(service).documentContent(100L, 12L, 21L);
    }
}
