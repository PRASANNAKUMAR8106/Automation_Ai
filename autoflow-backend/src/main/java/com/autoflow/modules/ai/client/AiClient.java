package com.autoflow.modules.ai.client;

import com.autoflow.modules.ai.dto.AiProviderType;

/**
 * Service Provider Interface for Generative AI model integrations.
 */
public interface AiClient {

    /**
     * Generates a conversational response or workflow automation completion.
     *
     * @param systemPrompt instruction/persona prompt
     * @param userMessage  incoming social media comment, DM, or inquiry
     * @return generated textual response
     */
    String generateReply(String systemPrompt, String userMessage);

    /**
     * The provider type implemented by this client.
     */
    AiProviderType getProviderType();

    /**
     * Checks if this provider is configured with a valid API key and endpoint.
     */
    boolean isAvailable();

    /**
     * The configured model ID currently in use (e.g. gpt-4o, gemini-1.5-pro, etc.).
     */
    String getModelId();
}
