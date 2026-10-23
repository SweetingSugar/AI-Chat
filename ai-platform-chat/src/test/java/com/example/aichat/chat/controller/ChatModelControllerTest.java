package com.example.aichat.chat.controller;

import com.example.aichat.ai.api.AiModelQueryService;
import com.example.aichat.ai.dto.response.AiModelDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatModelControllerTest {
    @Test
    void listReturnsAvailableModels() throws Exception {
        AiModelQueryService modelQueryService = mock(AiModelQueryService.class);
        when(modelQueryService.listAvailableModels()).thenReturn(List.of(
                new AiModelDTO(1L, 2L, "OpenAI", "gpt-test", "GPT Test", 8192, 1024, true, true)
        ));
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new ChatModelController(modelQueryService))
                .build();

        mockMvc.perform(get("/api/chat/models"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].providerName").value("OpenAI"))
                .andExpect(jsonPath("$.data[0].modelCode").value("gpt-test"))
                .andExpect(jsonPath("$.data[0].defaultModel").value(true));

        verify(modelQueryService).listAvailableModels();
    }
}
