package com.autoflow.modules.ai.config;

import com.autoflow.modules.ai.dto.AiProviderType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Externalized configuration properties for AI providers and model IDs.
 * Prevents hardcoding model IDs and allows runtime or environment overrides.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "autoflow.ai")
public class AiModelProperties {

    private AiProviderType defaultProvider = AiProviderType.MOCK;
    private double defaultTemperature = 0.7;
    private int defaultMaxTokens = 1024;
    private Map<String, ProviderConfig> providers = new HashMap<>();

    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String model;
        private String baseUrl;
        private boolean enabled = true;
    }

    public ProviderConfig getProviderConfig(AiProviderType type) {
        return providers.getOrDefault(type.name().toLowerCase(), new ProviderConfig());
    }
}
