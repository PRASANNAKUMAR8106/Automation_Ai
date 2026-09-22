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
public class AnthropicClient implements AiClient {

    private final AiModelProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AnthropicClient(AiModelProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String baseUrl = properties.getProviderConfig(AiProviderType.ANTHROPIC).getBaseUrl();
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.anthropic.com/v1")
                .build();
    }

    @Override
    public String generateReply(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new IllegalStateException("Anthropic client is not configured with an API key");
        }

        var config = properties.getProviderConfig(AiProviderType.ANTHROPIC);
        String model = getModelId();

        try {
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "max_tokens", properties.getDefaultMaxTokens(),
                    "system", systemPrompt != null ? systemPrompt : "",
                    "messages", List.of(
                            Map.of("role", "user", "content", userMessage != null ? userMessage : "")
                    )
            );

            String response = restClient.post()
                    .uri("/messages")
                    .header("x-api-key", config.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", e.getMessage());
            throw new RuntimeException("Anthropic generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.ANTHROPIC;
    }

    @Override
    public boolean isAvailable() {
        var config = properties.getProviderConfig(AiProviderType.ANTHROPIC);
        return config.isEnabled() && config.getApiKey() != null && !config.getApiKey().isBlank();
    }

    @Override
    public String getModelId() {
        var config = properties.getProviderConfig(AiProviderType.ANTHROPIC);
        return (config.getModel() != null && !config.getModel().isBlank()) ? config.getModel() : "claude-3-5-sonnet-20241022";
    }
}
