package com.autoflow.modules.ai.service;

import com.autoflow.modules.ai.client.AiClient;
import com.autoflow.modules.ai.config.AiModelProperties;
import com.autoflow.modules.ai.dto.AiProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Intelligent router dispatching AI generation tasks across configurable LLM providers
 * with automatic fallback to prevent workflow disruption.
 */
@Slf4j
@Service
public class AiRouterService {

    private final AiModelProperties properties;
    private final Map<AiProviderType, AiClient> clientMap = new EnumMap<>(AiProviderType.class);

    public AiRouterService(AiModelProperties properties, List<AiClient> clients) {
        this.properties = properties;
        for (AiClient client : clients) {
            clientMap.put(client.getProviderType(), client);
        }
    }

    /**
     * Generates an automated social-media reply using the default configured provider,
     * falling back seamlessly to alternate providers if unavailable.
     */
    public String generateSocialReply(String personaPrompt, String incomingMessage) {
        AiProviderType preferred = properties.getDefaultProvider();
        return generateWithFallback(preferred, personaPrompt, incomingMessage);
    }

    public String generateWithProvider(AiProviderType providerType, String personaPrompt, String incomingMessage) {
        return generateWithFallback(providerType, personaPrompt, incomingMessage);
    }

    private String generateWithFallback(AiProviderType preferred, String personaPrompt, String incomingMessage) {
        AiClient primary = clientMap.get(preferred);
        if (primary != null && primary.isAvailable()) {
            try {
                log.debug("Generating AI reply using preferred provider [{}] with model [{}]",
                        preferred, primary.getModelId());
                return primary.generateReply(personaPrompt, incomingMessage);
            } catch (Exception e) {
                log.warn("Primary AI provider [{}] failed: {}. Initiating fallback sequence...", preferred, e.getMessage());
            }
        }

        // Fallback sequence: Gemini -> OpenAI -> Anthropic -> Mock
        List<AiProviderType> fallbackOrder = List.of(
                AiProviderType.GEMINI,
                AiProviderType.OPENAI,
                AiProviderType.ANTHROPIC,
                AiProviderType.MOCK
        );

        for (AiProviderType fallback : fallbackOrder) {
            if (fallback == preferred) continue; // Already tried
            AiClient candidate = clientMap.get(fallback);
            if (candidate != null && candidate.isAvailable()) {
                try {
                    log.info("Falling back to AI provider [{}] with model [{}]", fallback, candidate.getModelId());
                    return candidate.generateReply(personaPrompt, incomingMessage);
                } catch (Exception e) {
                    log.warn("Fallback provider [{}] failed: {}", fallback, e.getMessage());
                }
            }
        }

        // Guaranteed fallback to mock if all else fails
        AiClient mockClient = clientMap.get(AiProviderType.MOCK);
        if (mockClient != null) {
            return mockClient.generateReply(personaPrompt, incomingMessage);
        }

        throw new IllegalStateException("All configured AI providers failed and no mock fallback was registered");
    }

    public String getActiveModel(AiProviderType providerType) {
        AiClient client = clientMap.get(providerType);
        return client != null ? client.getModelId() : "none";
    }
}
