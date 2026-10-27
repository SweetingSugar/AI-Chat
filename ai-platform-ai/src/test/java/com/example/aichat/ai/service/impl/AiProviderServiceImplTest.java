package com.example.aichat.ai.service.impl;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.ai.dto.request.AiProviderUpsertRequest;
import com.example.aichat.ai.entity.AiProvider;
import com.example.aichat.ai.enums.AiProviderType;
import com.example.aichat.ai.mapper.AiModelMapper;
import com.example.aichat.ai.mapper.AiProviderMapper;
import com.example.aichat.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiProviderServiceImplTest {
    @Mock
    private AiProviderMapper providerMapper;
    @Mock
    private AiModelMapper modelMapper;

    private AiProviderServiceImpl service;
    private AiCredentialCipher credentialCipher;

    @BeforeEach
    void setUp() {
        credentialCipher = new AiCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        service = new AiProviderServiceImpl(providerMapper, modelMapper, credentialCipher);
        when(providerMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void createsOllamaProviderWithoutApiKey() {
        AiProviderUpsertRequest request = request(AiProviderType.OLLAMA, null);

        assertDoesNotThrow(() -> service.create(request));
        verify(providerMapper).insert(argThat((AiProvider provider) -> provider.getApiKeyCiphertext() == null));
    }

    @Test
    void rejectsMissingApiKeyForNonOllamaProvider() {
        AiProviderUpsertRequest request = request(AiProviderType.OPENAI, null);

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void updateWithoutApiKeyPreservesExistingCredential() {
        String encryptedCredential = credentialCipher.encrypt("existing-key");
        AiProvider provider = new AiProvider();
        provider.setId(7L);
        provider.setProviderCode("ollama");
        provider.setProviderName("Ollama");
        provider.setProviderType(AiProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        provider.setApiKeyCiphertext(encryptedCredential);
        provider.setCredentialVersion(1);
        when(providerMapper.selectById(7L)).thenReturn(provider);
        when(providerMapper.updateById(any(AiProvider.class))).thenReturn(1);

        service.update(7L, request(AiProviderType.OLLAMA, null));

        assertEquals(encryptedCredential, provider.getApiKeyCiphertext());
    }

    @Test
    void rejectsChangingToNonOllamaWithoutExistingOrNewCredential() {
        AiProvider provider = new AiProvider();
        provider.setId(8L);
        provider.setProviderCode("ollama");
        provider.setProviderName("Ollama");
        provider.setProviderType(AiProviderType.OLLAMA);
        provider.setBaseUrl("http://localhost:11434");
        provider.setApiKeyCiphertext(null);
        provider.setCredentialVersion(1);
        when(providerMapper.selectById(8L)).thenReturn(provider);

        assertThrows(BusinessException.class, () -> service.update(8L, request(AiProviderType.OPENAI, null)));
    }

    private AiProviderUpsertRequest request(AiProviderType providerType, String apiKey) {
        return new AiProviderUpsertRequest(
                "provider-code",
                "Provider",
                providerType,
                "http://localhost:11434",
                apiKey,
                10,
                60,
                1,
                0,
                null
        );
    }
}
