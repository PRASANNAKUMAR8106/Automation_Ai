package com.autoflow.modules.channel.spi;

import com.autoflow.modules.crm.entity.ChannelType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry holding all available SocialChannelProvider implementations.
 */
@Component
public class SocialChannelProviderRegistry {

    private final Map<ChannelType, SocialChannelProvider> providers = new EnumMap<>(ChannelType.class);

    public SocialChannelProviderRegistry(List<SocialChannelProvider> providerList) {
        for (SocialChannelProvider provider : providerList) {
            providers.put(provider.getChannelType(), provider);
        }
    }

    public Optional<SocialChannelProvider> getProvider(ChannelType channelType) {
        return Optional.ofNullable(providers.get(channelType));
    }

    public SocialChannelProvider getRequiredProvider(ChannelType channelType) {
        SocialChannelProvider provider = providers.get(channelType);
        if (provider == null) {
            throw new UnsupportedOperationException("No social channel provider registered for channel: " + channelType);
        }
        return provider;
    }

    public boolean supports(ChannelType channelType) {
        return providers.containsKey(channelType);
    }

    /**
     * Strict multi-layer firewall enforcement:
     * Guarantees that internal notes and supervisor whispers NEVER reach external social providers.
     * Evaluates trusted origin metadata (messageType, senderType) alongside explicit content/media markers.
     */
    public void assertPermittedOutbound(String messageType, String senderType, String content, String mediaUrl) {
        assertOutboundPermitted(messageType, senderType, content, mediaUrl);
    }

    /**
     * Stateless form of the outbound firewall policy so that every dispatch path
     * (CRM live chat, macro execution, campaign broadcasts) enforces exactly the same
     * rules without needing the registry bean to be injected.
     */
    public static void assertOutboundPermitted(String messageType, String senderType, String content, String mediaUrl) {
        if (messageType != null) {
            String upper = messageType.trim().toUpperCase();
            if (upper.contains("INTERNAL") || upper.contains("NOTE") || upper.contains("WHISPER")) {
                throw new SecurityException("FIREWALL BREACH: Internal notes and supervisor whispers cannot be dispatched to external social providers! Blocked messageType: " + messageType);
            }
        }
        if (senderType != null) {
            String upper = senderType.trim().toUpperCase();
            if (upper.contains("INTERNAL") || upper.contains("WHISPER")) {
                throw new SecurityException("FIREWALL BREACH: Disallowed senderType for external social dispatch: " + senderType);
            }
        }
        if (content != null && (content.startsWith("[INTERNAL_NOTE]") || content.startsWith("[SUPERVISOR_WHISPER]"))) {
            throw new SecurityException("FIREWALL BREACH: Content tagged as internal note or whisper cannot be dispatched to external social providers!");
        }
        if (mediaUrl != null && (mediaUrl.contains("[INTERNAL_NOTE]") || mediaUrl.contains("[SUPERVISOR_WHISPER]"))) {
            throw new SecurityException("FIREWALL BREACH: Media URL tagged as internal note or whisper cannot be dispatched to external social providers!");
        }
    }

    public void assertPermittedOutboundMessage(String messageType, String content) {
        assertOutboundPermitted(messageType, null, content, null);
    }

    public void assertPermittedOutboundMedia(String mediaUrl) {
        assertOutboundPermitted(null, null, null, mediaUrl);
    }
}
