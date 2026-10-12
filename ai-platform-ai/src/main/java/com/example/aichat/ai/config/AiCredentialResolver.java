package com.example.aichat.ai.config;

/**
 * Resolves the encrypted database credential without exposing it to callers or logs.
 */
@FunctionalInterface
public interface AiCredentialResolver {
    String resolve(String ciphertext);
}
