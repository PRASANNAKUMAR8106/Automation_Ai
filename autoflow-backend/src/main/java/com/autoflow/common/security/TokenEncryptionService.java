package com.autoflow.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * High-performance, cryptographically secure AES-256-GCM token encryption service.
 * <p>
 * Plaintext access tokens are encrypted with a 256-bit symmetric key and a randomized
 * 12-byte IV (initialization vector) per operation. The 128-bit GCM authentication tag
 * ensures ciphertext integrity and prevents tampering.
 */
@Service
public class TokenEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public TokenEncryptionService(
            @Value("${autoflow.channels.token-encryption-key:autoflow_aes256_secret_key_32b!}") String secretKeyString
    ) {
        this.secretKey = deriveSecretKey(secretKeyString);
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext string using AES-256-GCM with randomized IV.
     *
     * @param plainText token or sensitive string
     * @return URL-safe Base64 encoded string containing [12-byte IV + Ciphertext + 16-byte Auth Tag]
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();

            return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception e) {
            throw new SecurityException("Failed to encrypt sensitive token with AES-GCM", e);
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext payload and validates integrity.
     *
     * @param cipherTextBase64 URL-safe Base64 encoded ciphertext with prepended IV
     * @return decrypted plaintext string
     */
    public String decrypt(String cipherTextBase64) {
        if (cipherTextBase64 == null || cipherTextBase64.isEmpty()) {
            return cipherTextBase64;
        }

        try {
            byte[] combined = Base64.getUrlDecoder().decode(cipherTextBase64);
            if (combined.length < GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)) {
                throw new IllegalArgumentException("Ciphertext payload is too short to contain valid IV and tag");
            }

            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            buffer.get(iv);

            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plainTextBytes = cipher.doFinal(cipherText);
            return new String(plainTextBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt or authenticate token (possible tampering or key mismatch)", e);
        }
    }

    private SecretKey deriveSecretKey(String keyString) {
        try {
            byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length == 32) {
                return new SecretKeySpec(keyBytes, "AES");
            }
            // Derive consistent 256-bit key via SHA-256 if key string length is not exactly 32 bytes
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] derivedBytes = sha256.digest(keyBytes);
            return new SecretKeySpec(derivedBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable on JVM", e);
        }
    }
}
