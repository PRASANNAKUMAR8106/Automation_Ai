package com.autoflow.modules.media.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.media.dto.AiMediaGenerateRequest;
import com.autoflow.modules.media.service.AiMediaGeneratorService;
import com.autoflow.modules.media.service.MediaStorageService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AiMediaController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AI Media Controller Tests")
class AiMediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiMediaGeneratorService aiMediaGeneratorService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /api/v1/media/ai/generate - Successful synthesis")
    void testGenerateAiMedia_Success() throws Exception {
        AiMediaGenerateRequest request = AiMediaGenerateRequest.builder()
                .templateType("COUPON_CARD")
                .prompt("VIP Discount Voucher")
                .headline("25% OFF Summer Sale")
                .subtext("Claim with code SAVE25")
                .badgeText("EXCLUSIVE")
                .accentColor("#6366F1")
                .width(1200)
                .height(630)
                .dynamicTokens(Map.of("code", "SAVE25"))
                .build();

        UUID assetId = UUID.randomUUID();
        MediaStorageService.MediaUploadResponse mockResponse = new MediaStorageService.MediaUploadResponse(
                assetId,
                testOrgId,
                "ai_asset_999.png",
                "image/png",
                65432L,
                "sha256_mock_hash",
                "https://s3.autoflow.ai/tenants/media/ai_asset_999.png"
        );

        when(aiMediaGeneratorService.generateBrandedAsset(eq(testOrgId), any(AiMediaGenerateRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/media/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assetId.toString()))
                .andExpect(jsonPath("$.data.fileName").value("ai_asset_999.png"))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://s3.autoflow.ai/tenants/media/ai_asset_999.png"));

        verify(aiMediaGeneratorService).generateBrandedAsset(eq(testOrgId), any(AiMediaGenerateRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/media/ai/generate - Validation failure when prompt is missing")
    void testGenerateAiMedia_ValidationFailure() throws Exception {
        AiMediaGenerateRequest request = AiMediaGenerateRequest.builder()
                .templateType("COUPON_CARD")
                .prompt("") // Blank prompt
                .build();

        mockMvc.perform(post("/api/v1/media/ai/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
