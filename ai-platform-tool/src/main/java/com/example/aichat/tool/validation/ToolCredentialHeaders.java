package com.example.aichat.tool.validation;

import com.example.aichat.ai.config.AiCredentialCipher;
import com.example.aichat.common.error.BusinessException;
import com.example.aichat.common.error.ErrorCode;
import com.example.aichat.tool.enums.ToolAuthType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolCredentialHeaders {
    private final AiCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;

    public ToolCredentialHeaders(AiCredentialCipher credentialCipher, ObjectMapper objectMapper) {
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
    }

    public String encryptCredential(String credential) {
        try {
            return credentialCipher.encrypt(credential);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Credential is invalid", exception);
        }
    }

    public String encryptSecretHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        try {
            return credentialCipher.encrypt(objectMapper.writeValueAsString(headers));
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Secret headers are invalid", exception);
        }
    }

    public Map<String, String> resolve(ToolAuthType authType,
                                       String authHeaderName,
                                       String credentialCiphertext,
                                       String secretHeadersCiphertext) {
        Map<String, String> result = new LinkedHashMap<>();
        if (secretHeadersCiphertext != null && !secretHeadersCiphertext.isBlank()) {
            result.putAll(decryptSecretHeaders(secretHeadersCiphertext));
        }
        if (authType == null || authType == ToolAuthType.NONE) {
            return Map.copyOf(result);
        }
        if (credentialCiphertext == null || credentialCiphertext.isBlank()) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Credential is missing");
        }
        String credential = decryptCredential(credentialCiphertext);
        if (authType == ToolAuthType.BEARER) {
            result.put("Authorization", "Bearer " + credential);
        } else if (authType == ToolAuthType.API_KEY) {
            if (authHeaderName == null || authHeaderName.isBlank()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Authentication header name is missing");
            }
            result.put(authHeaderName, credential);
        }
        return Map.copyOf(result);
    }

    private String decryptCredential(String ciphertext) {
        try {
            return credentialCipher.decrypt(ciphertext);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Credential is invalid", exception);
        }
    }

    private Map<String, String> decryptSecretHeaders(String ciphertext) {
        try {
            String plaintext = credentialCipher.decrypt(ciphertext);
            return objectMapper.readValue(plaintext, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_INVALID, "Secret headers are invalid", exception);
        }
    }
}
