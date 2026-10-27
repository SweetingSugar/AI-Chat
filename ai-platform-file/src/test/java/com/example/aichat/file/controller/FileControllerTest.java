package com.example.aichat.file.controller;

import com.example.aichat.file.service.FileService;
import com.example.aichat.file.vo.FileMetadataVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    private FileService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(service)).build();
    }

    @Test
    void uploadsMultipartFile() throws Exception {
        when(service.upload(any())).thenReturn(new FileMetadataVO(1L, 100L, "a.txt", "text/plain",
                5L, null, null, null));

        mockMvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", "a.txt", "text/plain", "hello".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void uploadsMultipleMultipartFiles() throws Exception {
        when(service.uploadBatch(any())).thenReturn(List.of(
                new FileMetadataVO(1L, 100L, "a.txt", "text/plain", 5L, null, null, null),
                new FileMetadataVO(2L, 100L, "b.txt", "text/plain", 5L, null, null, null)));

        mockMvc.perform(multipart("/api/files/batch")
                        .file(new MockMultipartFile("files", "a.txt", "text/plain", "hello".getBytes()))
                        .file(new MockMultipartFile("files", "b.txt", "text/plain", "world".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[1].id").value(2));
    }

    @Test
    void downloadsFileWithMetadataHeaders() throws Exception {
        when(service.findAccessible(1L)).thenReturn(new FileMetadataVO(1L, 100L, "a.txt", "text/plain",
                5L, null, null, null));
        when(service.download(1L)).thenReturn(new InputStreamResource(new ByteArrayInputStream("hello".getBytes())));

        mockMvc.perform(get("/api/files/1/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().string("hello"));
    }
}
