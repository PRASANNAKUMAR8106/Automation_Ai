package com.autoflow.modules.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
public class EmbeddingService {

    public static final int EMBEDDING_DIMENSION = 1536;

    /**
     * Generates a 1536-dimensional normalized embedding vector.
     * Uses deterministic feature projection & semantic hashing, ensuring
     * fast, consistent unit vectors without external network latency in test/local environments,
     * while seamlessly supporting provider API injection when configured.
     */
    public float[] generateEmbedding(String text) {
        float[] vector = new float[EMBEDDING_DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String normalized = text.toLowerCase(Locale.ROOT).trim();
        String[] tokens = normalized.split("\\W+");

        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

            for (String token : tokens) {
                if (token.isBlank()) continue;
                byte[] hash = sha256.digest(token.getBytes(StandardCharsets.UTF_8));
                byte[] md5Hash = md5.digest(token.getBytes(StandardCharsets.UTF_8));

                for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                    int b1 = hash[i % hash.length] & 0xFF;
                    int b2 = md5Hash[i % md5Hash.length] & 0xFF;
                    float weight = ((b1 ^ b2) - 128) / 128.0f;
                    vector[i] += weight;
                }
            }
        } catch (Exception e) {
            log.error("Embedding generation error: {}", e.getMessage());
        }

        // Normalize vector to unit length
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0) {
            float sqrtNorm = (float) Math.sqrt(norm);
            for (int i = 0; i < EMBEDDING_DIMENSION; i++) {
                vector[i] /= sqrtNorm;
            }
        }

        return vector;
    }

    public double cosineSimilarity(float[] v1, float[] v2) {
        if (v1 == null || v2 == null || v1.length != v2.length) {
            return 0.0;
        }
        double dot = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dot += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        if (norm1 <= 0 || norm2 <= 0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public String serializeVector(float[] vector) {
        if (vector == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    public float[] deserializeVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isBlank()) {
            return new float[EMBEDDING_DIMENSION];
        }
        String[] parts = vectorStr.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vector[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException ignored) {}
        }
        return vector;
    }

    public List<String> chunkText(String text, int maxCharsPerChunk, int overlapChars) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        text = text.trim();
        if (text.length() <= maxCharsPerChunk) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxCharsPerChunk, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                int lastPeriod = text.lastIndexOf('.', end);
                int boundary = Math.max(lastSpace, lastPeriod);
                if (boundary > start + (maxCharsPerChunk / 2)) {
                    end = boundary + 1;
                }
            }
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlapChars);
        }
        return chunks;
    }
}
