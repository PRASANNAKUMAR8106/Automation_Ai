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
}
