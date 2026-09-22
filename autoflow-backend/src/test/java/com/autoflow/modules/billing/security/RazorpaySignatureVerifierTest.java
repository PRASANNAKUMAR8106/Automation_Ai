package com.autoflow.modules.billing.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Razorpay Signature Verifier Test")
class RazorpaySignatureVerifierTest {

    private static final String TEST_SECRET = "test_webhook_secret_xyz123";
    private RazorpaySignatureVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new RazorpaySignatureVerifier(TEST_SECRET);
    }

    private String calculateExpectedSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Should accept valid HMAC-SHA256 signature")
    void shouldAcceptValidSignature() throws Exception {
        String payload = "{\"event\":\"payment.captured\",\"amount\":49900}";
        String signature = calculateExpectedSignature(payload, TEST_SECRET);

        assertTrue(verifier.verifySignature(payload, signature));
        assertTrue(verifier.verify(payload, signature));
    }

    @Test
    @DisplayName("Should reject tampered payload")
    void shouldRejectTamperedPayload() throws Exception {
        String originalPayload = "{\"event\":\"payment.captured\",\"amount\":49900}";
        String signature = calculateExpectedSignature(originalPayload, TEST_SECRET);

        String tamperedPayload = "{\"event\":\"payment.captured\",\"amount\":99900}";
        assertFalse(verifier.verifySignature(tamperedPayload, signature));
    }

    @Test
    @DisplayName("Should reject signature generated with wrong secret")
    void shouldRejectWrongSecret() throws Exception {
        String payload = "{\"event\":\"subscription.activated\"}";
        String invalidSignature = calculateExpectedSignature(payload, "wrong_secret");

        assertFalse(verifier.verifySignature(payload, invalidSignature));
    }

    @Test
    @DisplayName("Should reject null or blank signatures")
    void shouldRejectNullOrBlankSignatures() {
        String payload = "{\"event\":\"test\"}";
        assertFalse(verifier.verifySignature(payload, null));
        assertFalse(verifier.verifySignature(payload, ""));
        assertFalse(verifier.verifySignature(payload, "   "));
        assertFalse(verifier.verifySignature(null, "some_sig"));
    }

    @Test
    @DisplayName("Should verify with explicit secret argument")
    void shouldVerifyWithExplicitSecret() throws Exception {
        String customSecret = "custom_secret_key_456";
        String payload = "{\"event\":\"subscription.charged\"}";
        String signature = calculateExpectedSignature(payload, customSecret);

        assertTrue(verifier.verifySignature(payload, signature, customSecret));
        assertFalse(verifier.verifySignature(payload, signature, "another_secret"));
    }
}
