package com.example.aichat.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentAdminVO;
import com.example.aichat.common.api.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminAgentControllerTest {
    @Mock
    private AgentService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminAgentController(service)).build();
    }

    @Test
    void pagesAllAgents() throws Exception {
        when(service.adminPage(2, 30)).thenReturn(new PageResult<>(List.of(
                new AgentAdminVO(100L, 7L, "Support", "Support agent", "You are support.", 2L,
                        List.of(11L), List.of("order_query"), 1, null, null)),
                1, 2, 30, 1));

        mockMvc.perform(get("/api/admin/agents")
                        .param("page", "2")
                        .param("size", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(100))
                .andExpect(jsonPath("$.data.records[0].userId").value(7));

        verify(service).adminPage(2, 30);
    }

    @Test
    void getsAgentForAdmin() throws Exception {
        when(service.adminGet(100L)).thenReturn(new AgentAdminVO(100L, 7L, "Support", "Support agent",
                "You are support.", 2L, List.of(), List.of(), 1, null, null));

        mockMvc.perform(get("/api/admin/agents/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.userId").value(7));

        verify(service).adminGet(100L);
    }

    @Test
    void protectsBothEndpointsWithAgentReadPermission() throws Exception {
        Method page = AdminAgentController.class.getDeclaredMethod("page", int.class, int.class);
        Method get = AdminAgentController.class.getDeclaredMethod("get", Long.class);

        assertThat(page.getAnnotation(SaCheckPermission.class).value()).isEqualTo("agent:read");
        assertThat(get.getAnnotation(SaCheckPermission.class).value()).isEqualTo("agent:read");
    }
}
