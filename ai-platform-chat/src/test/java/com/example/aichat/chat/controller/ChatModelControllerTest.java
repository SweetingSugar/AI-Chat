package com.example.aichat.chat.controller;

import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.dto.response.AiEmbeddingModelDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatModelControllerTest {
    @Mock
    private AiModelQueryService modelQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatModelController(modelQueryService)).build();
    }

    @Test
    void listsAvailableEmbeddingModels() throws Exception {
        when(modelQueryService.listAvailableEmbeddingModels()).thenReturn(List.of(
                new AiEmbeddingModelDTO(2L, 1L, "OpenAI", "text-embedding-3-small",
                        "Text Embedding 3 Small", 1536)));

        mockMvc.perform(get("/api/chat/models/embedding"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].providerName").value("OpenAI"))
                .andExpect(jsonPath("$.data[0].modelCode").value("text-embedding-3-small"))
                .andExpect(jsonPath("$.data[0].embeddingDimension").value(1536));

        verify(modelQueryService).listAvailableEmbeddingModels();
    }
}
