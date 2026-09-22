package com.autoflow.modules.ai.service;

import com.autoflow.modules.ai.client.AiClient;
import com.autoflow.modules.ai.client.MockAiClient;
import com.autoflow.modules.ai.config.AiModelProperties;
import com.autoflow.modules.ai.dto.AiProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRouterServiceTest {

    @Mock
    private AiClient openAiClient;

    @Mock
    private AiClient geminiClient;

    private MockAiClient mockClient;
    private AiModelProperties properties;
    private AiRouterService routerService;

    @BeforeEach
    void setUp() {
        mockClient = new MockAiClient();
        properties = new AiModelProperties();

        when(openAiClient.getProviderType()).thenReturn(AiProviderType.OPENAI);
        when(geminiClient.getProviderType()).thenReturn(AiProviderType.GEMINI);

        routerService = new AiRouterService(properties, List.of(openAiClient, geminiClient, mockClient));
    }

    @Test
    @DisplayName("Primary configured provider is used when healthy and available")
    void testPrimaryProviderUsed() {
        properties.setDefaultProvider(AiProviderType.OPENAI);
        when(openAiClient.isAvailable()).thenReturn(true);
        when(openAiClient.generateReply(anyString(), anyString())).thenReturn("OpenAI custom response");

        String reply = routerService.generateSocialReply("You are an assistant", "Hello");
        assertEquals("OpenAI custom response", reply);
    }

    @Test
    @DisplayName("When primary provider throws exception, router falls back gracefully to secondary")
    void testFallbackOnException() {
        properties.setDefaultProvider(AiProviderType.OPENAI);
        when(openAiClient.isAvailable()).thenReturn(true);
        when(openAiClient.generateReply(anyString(), anyString())).thenThrow(new RuntimeException("OpenAI rate limited"));

        when(geminiClient.isAvailable()).thenReturn(true);
        when(geminiClient.generateReply(anyString(), anyString())).thenReturn("Gemini fallback response");

        String reply = routerService.generateSocialReply("You are an assistant", "Hello");
        assertEquals("Gemini fallback response", reply);
    }

    @Test
    @DisplayName("When all external providers fail, router falls back to MockAiClient with zero downtime")
    void testFallbackToMockClient() {
        properties.setDefaultProvider(AiProviderType.OPENAI);
        when(openAiClient.isAvailable()).thenReturn(false);
        when(geminiClient.isAvailable()).thenReturn(false);

        String reply = routerService.generateSocialReply("System", "What is the price of starter plan?");
        assertNotNull(reply);
        assertTrue(reply.contains("₹1,499"));
    }

    @Test
    @DisplayName("Model IDs are dynamically configurable in properties")
    void testConfigurableModelIds() {
        var openaiConfig = new AiModelProperties.ProviderConfig();
        openaiConfig.setModel("gpt-4o-2026-custom");
        properties.getProviders().put("openai", openaiConfig);

        when(openAiClient.getModelId()).thenReturn("gpt-4o-2026-custom");
        assertEquals("gpt-4o-2026-custom", routerService.getActiveModel(AiProviderType.OPENAI));
    }
}
