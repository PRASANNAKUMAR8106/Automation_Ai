package com.autoflow.modules.channel.dto;

import com.autoflow.modules.crm.entity.ChannelType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthInitiateResponse {
    private String authorizationUrl;
    private String state;
    private ChannelType channel;
}
