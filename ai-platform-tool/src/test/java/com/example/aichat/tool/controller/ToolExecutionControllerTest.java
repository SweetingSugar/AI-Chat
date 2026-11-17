package com.example.aichat.tool.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.example.aichat.tool.dto.execution.ToolExecuteRequest;
import com.example.aichat.tool.dto.query.AvailableToolView;
import com.example.aichat.tool.enums.ToolType;
import com.example.aichat.tool.service.ToolExecutionService;
import com.example.aichat.tool.service.ToolQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class ToolExecutionControllerTest {
    @Mock
    private ToolExecutionService executionService;
    @Mock
    private ToolQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ToolExecutionController(executionService, queryService)).build();
    }

    @Test
    void listsAvailableToolsWithoutPermissionAnnotation() throws Exception {
        when(queryService.listAvailable()).thenReturn(List.of(
                new AvailableToolView("weather_current", "Current weather", "Query weather",
                        ToolType.MCP, new ObjectMapper().readTree("{\"type\":\"object\"}"))));

        mockMvc.perform(get("/api/tools/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].toolCode").value("weather_current"))
                .andExpect(jsonPath("$.data[0].toolName").value("Current weather"))
                .andExpect(jsonPath("$.data[0].toolType").value("MCP"))
                .andExpect(jsonPath("$.data[0].inputSchema.type").value("object"))
                .andExpect(jsonPath("$.data[0].httpConfig").doesNotExist())
                .andExpect(jsonPath("$.data[0].credentialVersion").doesNotExist());

        verify(queryService).listAvailable();
    }

    @Test
    void keepsPermissionCheckOnlyOnExecutionMethod() throws Exception {
        Method listMethod = ToolExecutionController.class.getDeclaredMethod("listAvailable");
        Method executeMethod = ToolExecutionController.class.getDeclaredMethod(
                "execute", ToolExecuteRequest.class);

        assertThat(listMethod.getAnnotation(SaCheckPermission.class)).isNull();
        assertThat(executeMethod.getAnnotation(SaCheckPermission.class)).isNotNull();
        assertThat(executeMethod.getAnnotation(SaCheckPermission.class).value())
                .isEqualTo("tool:execute");
    }
}
