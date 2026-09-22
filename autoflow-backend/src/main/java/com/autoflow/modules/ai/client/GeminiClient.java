package com.autoflow.modules.ai.client;

import com.autoflow.modules.ai.config.AiModelProperties;
import com.autoflow.modules.ai.dto.AiProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class GeminiClient implements AiClient {

    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiClient(AiModelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = properties.getProviderConfig(AiProviderType.GEMINI).getBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://generativelanguage.googleapis.com/v1beta")
                .build();
    }

    @Override
    public String generateReply(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new IllegalStateException("Gemini client is not configured with an API key");
        }

        var config = properties.getProviderConfig(AiProviderType.GEMINI);
        String model = getModelId();

        try {
            Map<String, Object> payload = Map.of(
                    "contents", List.of(
                            Map.of("role", "user", "parts", List.of(Map.of("text", (systemPrompt != null ? systemPrompt + "\n\n" : "") + userMessage)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", properties.getDefaultTemperature(),
                            "maxOutputTokens", properties.getDefaultMaxTokens()
                    )
            );

            String response = restClient.post()
                    .uri("/models/" + model + ":generateContent?key=" + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage());
            throw new RuntimeException("Gemini generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.GEMINI;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getProviderConfig(AiProviderType.GEMINI);
        return config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String getModelId() {
        var config = properties.getProviderConfig(AiProviderType.GEMINI);
        return (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "gemini-1.5-pro";
    }
}
