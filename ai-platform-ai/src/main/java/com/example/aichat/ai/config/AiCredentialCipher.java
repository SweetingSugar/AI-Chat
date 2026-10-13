package com.example.aichat.ai.config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AiCredentialCipher {
    private static final String VERSION = "v1";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String encodedKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiCredentialCipher(String encodedKey) {
        this.encodedKey = encodedKey;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) throw new IllegalArgumentException("Credential must not be blank");
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            return VERSION + ":" + Base64.getEncoder().encodeToString(payload);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to encrypt AI credential", exception);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(VERSION + ":")) {
            throw new IllegalArgumentException("Invalid encrypted credential");
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext.substring(VERSION.length() + 1));
            if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted credential");
            byte[] nonce = Arrays.copyOfRange(payload, 0, NONCE_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalArgumentException("Invalid encrypted credential", exception);
        }
    }

    private SecretKey key() {
        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException("AI credential encryption key is not configured");
        }
        try {
            byte[] key = Base64.getDecoder().decode(encodedKey);
            if (key.length != KEY_BYTES) throw new IllegalStateException("AI credential encryption key must be 256 bits");
            return new SecretKeySpec(key, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("AI credential encryption key is invalid", exception);
        }
    }
}
