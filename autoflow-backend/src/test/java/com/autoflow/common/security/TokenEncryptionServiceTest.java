package com.autoflow.common.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenEncryptionServiceTest {

    private TokenEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new TokenEncryptionService("test_secret_key_32_bytes_long!");
    }

    @Test
    @DisplayName("Encrypt and decrypt successfully round-trips sensitive token")
    void testEncryptDecryptRoundTrip() {
        String originalToken = "EAAGm0PX4ZCpsBAK123456789LongLivedInstagramUserAccessTokenXYZ";

        String encrypted = encryptionService.encrypt(originalToken);
        assertNotNull(encrypted);
        assertNotEquals(originalToken, encrypted);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(originalToken, decrypted);
    }

    @Test
    @DisplayName("Subsequent encryptions of same plaintext produce different ciphertexts due to random IV")
    void testRandomIvProducesDifferentCiphertexts() {
        String token = "IGQVJYeE1234567890";

        String ciphertext1 = encryptionService.encrypt(token);
        String ciphertext2 = encryptionService.encrypt(token);

        assertNotEquals(ciphertext1, ciphertext2);
        // Both must decrypt back to original token
        assertEquals(token, encryptionService.decrypt(ciphertext1));
        assertEquals(token, encryptionService.decrypt(ciphertext2));
    }

    @Test
    @DisplayName("Tampered ciphertext fails GCM authentication tag check and throws SecurityException")
    void testTamperedCiphertextThrowsSecurityException() {
        String token = "SecretToken123";
        String encrypted = encryptionService.encrypt(token);

        byte[] bytes = Base64.getUrlDecoder().decode(encrypted);
        // Flip one bit in ciphertext
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        assertThrows(SecurityException.class, () -> encryptionService.decrypt(tampered));
    }

    @Test
    @DisplayName("Decrypting with different secret key throws SecurityException")
    void testWrongKeyThrowsSecurityException() {
        String token = "ConfidentialChannelToken";
        String encrypted = encryptionService.encrypt(token);

        TokenEncryptionService differentKeyService = new TokenEncryptionService("different_key_32_bytes_long_here!");
        assertThrows(SecurityException.class, () -> differentKeyService.decrypt(encrypted));
    }

    @Test
    @DisplayName("Null or empty strings pass through unchanged")
    void testNullOrEmptyPassThrough() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
        assertEquals("", encryptionService.encrypt(""));
        assertEquals("", encryptionService.decrypt(""));
    }
}
