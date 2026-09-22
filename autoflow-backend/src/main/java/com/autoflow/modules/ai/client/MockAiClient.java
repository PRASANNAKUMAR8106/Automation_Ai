package com.autoflow.modules.ai.client;

import com.autoflow.modules.ai.dto.AiProviderType;
import org.springframework.stereotype.Component;

@Component
public class MockAiClient implements AiClient {

    @Override
    public String generateReply(String systemPrompt, String userMessage) {
        String lower = (userMessage != null) ? userMessage.toLowerCase() : "";
        if (lower.contains("price") || lower.contains("cost")) {
            return "Hey there! Our starter plans begin at ₹1,499/mo. Check out the link in our bio for full pricing options! 🚀";
        }
        if (lower.contains("guide") || lower.contains("pdf")) {
            return "Thanks for reaching out! Your free guide has been sent to your DMs. Let us know what you think! 🎁";
        }
        if (lower.contains("demo") || lower.contains("meeting")) {
            return "We'd love to show you how AutoFlow works! Pick a 15-minute slot on our calendar: https://autoflow.ai/demo 📅";
        }
        return "Hi! Thanks for connecting with us on AutoFlow. How can we help automate your workflow today?";
    }

    @Override
    public AiProviderType getProviderType() {
        return AiProviderType.MOCK;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String getModelId() {
        return "mock-autoflow-v1";
    }
}
