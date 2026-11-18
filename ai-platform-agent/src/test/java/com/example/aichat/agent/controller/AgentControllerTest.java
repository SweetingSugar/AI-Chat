package com.example.aichat.agent.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.agent.dto.AgentStatusRequest;
import com.example.aichat.agent.dto.AgentUpsertRequest;
import com.example.aichat.agent.service.AgentService;
import com.example.aichat.agent.vo.AgentVO;
import com.example.aichat.common.security.CurrentUser;
import com.example.aichat.common.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {
    @Mock
    private AgentService service;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentController(service, currentUserProvider)).build();
    }

    @Test
    void createsAgentForCurrentUser() throws Exception {
        when(currentUserProvider.requireCurrentUser()).thenReturn(new CurrentUser(7L, "user"));
        when(service.create(eq(7L), any(AgentUpsertRequest.class))).thenReturn(agentView(100L));

        mockMvc.perform(post("/api/agents")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100));

        verify(service).create(eq(7L), any(AgentUpsertRequest.class));
    }

    @Test
    void exposesUserScopedEndpointsWithoutPermissionAnnotations() throws Exception {
        for (String name : List.of("page", "get", "create", "update", "delete", "updateStatus")) {
            Method method = switch (name) {
                case "page" -> AgentController.class.getDeclaredMethod("page", int.class, int.class);
                case "get" -> AgentController.class.getDeclaredMethod("get", Long.class);
                case "create" -> AgentController.class.getDeclaredMethod("create", AgentUpsertRequest.class);
                case "update" -> AgentController.class.getDeclaredMethod("update", Long.class,
                        AgentUpsertRequest.class);
                case "delete" -> AgentController.class.getDeclaredMethod("delete", Long.class);
                case "updateStatus" -> AgentController.class.getDeclaredMethod("updateStatus", Long.class,
                        AgentStatusRequest.class);
                default -> throw new IllegalStateException(name);
            };
            assertThat(method.getAnnotation(SaCheckPermission.class)).isNull();
        }
    }

    private AgentUpsertRequest validRequest() {
        return new AgentUpsertRequest("Support", "Support agent", "You are support.",
                2L, List.of(), List.of());
    }

    private AgentVO agentView(Long id) {
        return new AgentVO(id, "Support", "Support agent", "You are support.", 2L,
                List.of(), List.of(), 1, null, null);
    }
}
