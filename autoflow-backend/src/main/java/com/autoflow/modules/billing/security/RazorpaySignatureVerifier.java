package com.autoflow.modules.billing.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Validates Razorpay Webhook signatures using HMAC-SHA256.
 * Protects billing and subscription lifecycle endpoints against tampering and forgery.
 */
@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;

    public RazorpaySignatureVerifier(
            @Value("${autoflow.razorpay.webhook-secret:rzp_test_webhook_secret_2026}") String webhookSecret
    ) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * Verifies the authenticity of incoming Razorpay webhook payload.
     *
     * @param payload        raw JSON request body as string
     * @param expectedSignature signature string received in X-Razorpay-Signature header
     * @return true if valid and authentic, false otherwise
     */
    public boolean verifySignature(String payload, String expectedSignature) {
        return verifySignature(payload, expectedSignature, this.webhookSecret);
    }

    public boolean verifySignature(String payload, String expectedSignature, String secret) {
        if (payload == null || expectedSignature == null || expectedSignature.isBlank() || secret == null) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKey);

            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = HexFormat.of().formatHex(hash);

            // Constant-time equality comparison to prevent timing attacks
            return MessageDigest.isEqual(
                    calculatedSignature.getBytes(StandardCharsets.UTF_8),
                    expectedSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public boolean verify(String payload, String expectedSignature) {
        return verifySignature(payload, expectedSignature);
    }

    public boolean verify(String payload, String expectedSignature, String secret) {
        return verifySignature(payload, expectedSignature, secret);
    }
}
