package com.example.aichat.admin.controller;

import com.example.aichat.common.api.PageResult;
import com.example.aichat.file.dto.FileQueryRequest;
import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileDownloadUrlVO;
import com.example.aichat.file.vo.FileMetadataVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminFileControllerTest {

    @Mock
    private FileService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminFileController(service)).build();
    }

    @Test
    void pagesFiles() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(
                new FileMetadataVO(1L, 100L, "a.txt", "text/plain", 5L, null, null, null)),
                1, 1, 20, 1));

        mockMvc.perform(get("/api/admin/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.records[0].id").value(1));
    }

    @Test
    void generatesPresignedUrlForAnyFileWithDownloadPermission() throws Exception {
        when(service.createAdminDownloadUrl(1L))
                .thenReturn(new FileDownloadUrlVO(1L, "https://download.example/file", 600L));

        mockMvc.perform(get("/api/admin/files/1/presigned-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fileId").value(1))
                .andExpect(jsonPath("$.data.url").value("https://download.example/file"))
                .andExpect(jsonPath("$.data.expiresIn").value(600));
    }

    @Test
    void acceptsDateOnlyQueryParameters() throws Exception {
        when(service.page(any())).thenReturn(new PageResult<>(List.of(), 0, 1, 20, 0));

        mockMvc.perform(get("/api/admin/files")
                        .param("createdFrom", "2026-10-01")
                        .param("createdTo", "2026-10-27"))
                .andExpect(status().isOk());

        ArgumentCaptor<FileQueryRequest> requestCaptor = ArgumentCaptor.forClass(FileQueryRequest.class);
        verify(service).page(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getCreatedFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(requestCaptor.getValue().getCreatedTo()).isEqualTo(LocalDate.of(2026, 10, 27));
    }
}
