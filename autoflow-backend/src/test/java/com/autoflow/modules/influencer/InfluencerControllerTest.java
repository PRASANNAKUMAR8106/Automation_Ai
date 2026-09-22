package com.autoflow.modules.influencer;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.influencer.controller.InfluencerController;
import com.autoflow.modules.influencer.dto.*;
import com.autoflow.modules.influencer.entity.*;
import com.autoflow.modules.influencer.service.InfluencerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InfluencerController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InfluencerController Web MVC Tests")
class InfluencerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InfluencerService influencerService;

    @Test
    @DisplayName("POST /api/v1/admin/influencers creates influencer (Admin)")
    void testCreateInfluencer() throws Exception {
        CreateInfluencerRequest req = CreateInfluencerRequest.builder()
                .name("Partner X")
                .email("partnerx@example.com")
                .instagramHandle("@partner_x")
                .build();

        UUID influencerId = UUID.randomUUID();
        Influencer influencer = Influencer.builder().name("Partner X").email("partnerx@example.com").build();
        influencer.setId(influencerId);

        when(influencerService.createInfluencer(any(CreateInfluencerRequest.class))).thenReturn(influencer);

        mockMvc.perform(post("/api/v1/admin/influencers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Partner X"));
    }

    @Test
    @DisplayName("POST /api/v1/admin/influencer/campaigns creates campaign (Admin)")
    void testCreateCampaign() throws Exception {
        CreateCampaignRequest req = CreateCampaignRequest.builder()
                .name("Summer Launch")
                .discountValue(BigDecimal.valueOf(15.00))
                .commissionValue(BigDecimal.valueOf(25.00))
                .build();

        UUID campaignId = UUID.randomUUID();
        Campaign campaign = Campaign.builder().name("Summer Launch").build();
        campaign.setId(campaignId);

        when(influencerService.createCampaign(any(CreateCampaignRequest.class))).thenReturn(campaign);

        mockMvc.perform(post("/api/v1/admin/influencer/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Summer Launch"));
    }

    @Test
    @DisplayName("GET /api/v1/influencer/portal/stats returns stats for authenticated influencer")
    @WithMockUser(username = "influencer_user", roles = {"INFLUENCER"})
    void testGetPortalStats() throws Exception {
        UUID infId = UUID.randomUUID();
        InfluencerStatsResponse stats = InfluencerStatsResponse.builder()
                .influencerId(infId)
                .name("Alex Partner")
                .promoCode("ALEX20")
                .referralLink("http://localhost:8080/r/ALEX20")
                .totalClicks(450)
                .pendingCommissionInr(BigDecimal.valueOf(1500.00))
                .approvedCommissionInr(BigDecimal.valueOf(3200.00))
                .paidCommissionInr(BigDecimal.valueOf(5000.00))
                .build();

        when(influencerService.getInfluencerStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/influencer/portal/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Alex Partner"))
                .andExpect(jsonPath("$.data.totalClicks").value(450))
                .andExpect(jsonPath("$.data.approvedCommissionInr").value(3200.00));
    }
}
