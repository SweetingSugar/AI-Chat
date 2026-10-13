package com.example.aichat.ai.dto;

import com.example.aichat.ai.dto.response.AiModelManagementDTO;
import com.example.aichat.ai.dto.response.AiProviderManagementDTO;
import com.example.aichat.ai.enums.AiProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AiManagementDtoSerializationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void providerManagementResponseNeverSerializesCredentials() throws Exception {
        AiProviderManagementDTO response = new AiProviderManagementDTO(
                1L, "openai", "OpenAI", AiProviderType.OPENAI, "https://api.openai.com/v1",
                5, 10, 1, true, 1, 0, "primary");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"hasApiKey\":true")
                .doesNotContain("apiKey", "apiKeyCiphertext", "ciphertext");
    }

    @Test
    void modelManagementResponseDoesNotContainProviderCredentials() throws Exception {
        AiModelManagementDTO response = new AiModelManagementDTO(
                2L, 1L, "OpenAI", "gpt-4o-mini", "GPT-4o mini", "CHAT", 128000, 4096,
                new BigDecimal("0.70"), new BigDecimal("0.90"), true, true, 1, 1, 0, "default");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"providerName\":\"OpenAI\"")
                .doesNotContain("apiKey", "apiKeyCiphertext", "ciphertext");
    }
}
