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
public class OpenAiClient implements AiClient {

    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAiClient(AiModelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = properties.getProviderConfig(AiProviderType.OPENAI).getBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1")
                .build();
    }

    @Override
    public String generateReply(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenAI client is not configured with an API key");
        }

        var config = properties.getProviderConfig(AiProviderType.OPENAI);
        String model = getModelId();

        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "temperature", properties.getDefaultTemperature(),
                    "max_tokens", properties.getDefaultMaxTokens(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                            Map.of("role", "user", "content", userMessage != null ? userMessage : "")
                    )
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            throw new RuntimeException("OpenAI generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.OPENAI;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getProviderConfig(AiProviderType.OPENAI);
        return config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String getModelId() {
        var config = properties.getProviderConfig(AiProviderType.OPENAI);
        return (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "gpt-4o";
    }
}
