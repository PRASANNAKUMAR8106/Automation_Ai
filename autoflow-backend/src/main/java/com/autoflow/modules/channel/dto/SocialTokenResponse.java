package com.autoflow.modules.channel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialTokenResponse {
    private String accessToken;
    private String tokenType;
    private Long expiresInSeconds;
    private Instant expiresAt;
    private String rawResponse;
}
