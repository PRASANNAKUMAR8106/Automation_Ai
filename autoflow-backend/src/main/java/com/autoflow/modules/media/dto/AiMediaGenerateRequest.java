package com.autoflow.modules.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMediaGenerateRequest {

    /**
     * Template style: COUPON_CARD, LEAD_MAGNET_COVER, PROMO_BANNER, CERTIFICATE, ANNOUNCEMENT
     */
    @NotBlank(message = "Template type is required")
    @Builder.Default
    private String templateType = "COUPON_CARD";

    @NotBlank(message = "Prompt or title is required")
    private String prompt;

    private String headline;

    private String subtext;

    private String badgeText;

    private String accentColor; // Hex string e.g. #6366F1

    @Builder.Default
    private int width = 1200;

    @Builder.Default
    private int height = 630;

    private Map<String, String> dynamicTokens;
}
