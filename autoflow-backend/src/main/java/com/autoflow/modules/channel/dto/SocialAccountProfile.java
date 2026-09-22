package com.autoflow.modules.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialAccountProfile {
    private String externalAccountId;
    private String accountName;
    private String accountHandle;
    private String pageId;
    private String pageAccessToken;
    private String profilePictureUrl;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
