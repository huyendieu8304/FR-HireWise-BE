package com.hirewise.be.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM at-rest encryption for {@code oauth_tokens.access_token_encrypted}
 * / {@code refresh_token_encrypted}. Each call to {@link #encrypt} uses a fresh random
 * IV (stored alongside the ciphertext, as is standard for GCM - the IV
 * itself is not secret), so encrypting the same token twice never produces
 * the same output.
 * <p>
 * Mirrors {@code config.SecurityConfig#jwtSigningKey}: falls back to a
 * random per-boot key ONLY when {@code app.integration.token-encryption-key}
 * is left blank, so local/dev runs still work out of the box. Every other
 * profile MUST set it explicitly (see {@code .env.example}) - otherwise
 * every previously-stored token becomes undecryptable on restart (the
 * integration would show as Expired and require a Reconnect, UC-08).
 */
@Slf4j
@Component
public class TokenCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public TokenCipher(@Value("${app.integration.token-encryption-key:}") String base64Key) {
        byte[] keyBytes;
        if (base64Key == null || base64Key.isBlank()) {
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            log.warn("app.integration.token-encryption-key is not set - using a random per-boot key. "
                    + "Every stored Cloud Storage OAuth token will need a Reconnect (UC-08) after every restart. "
                    + "Set INTEGRATION_TOKEN_ENCRYPTION_KEY in every non-local environment.");
        } else {
            keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "app.integration.token-encryption-key must decode to exactly 32 bytes (256 bits) for AES-256");
            }
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * @param plaintext the raw token value
     * @return {@code base64(iv || ciphertext || authTag)}, safe to store in a TEXT column
     */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // AES-GCM with a valid 32-byte key never fails to encrypt in practice;
            // treated as a programming error rather than a recoverable business exception.
            throw new IllegalStateException("Failed to encrypt token", e);
        }
    }

    /**
     * @param encoded a value previously returned by {@link #encrypt}
     * @return the original plaintext token
     */
    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt token - wrong key, or the key changed since it was encrypted", e);
        }
    }
}
