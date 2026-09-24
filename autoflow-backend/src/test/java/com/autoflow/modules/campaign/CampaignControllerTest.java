package com.autoflow.modules.campaign;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.campaign.controller.CampaignController;
import com.autoflow.modules.campaign.dto.CampaignDto.*;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import com.autoflow.modules.campaign.service.CampaignService;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CampaignController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CampaignController Web MVC Tests")
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CampaignService campaignService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @Test
    @DisplayName("POST /api/v1/campaigns/estimate-audience returns calculated reach")
    void testEstimateAudience() throws Exception {
        AudienceEstimateRequest req = AudienceEstimateRequest.builder()
                .channel(ChannelType.INSTAGRAM)
                .minLeadScore(50)
                .build();

        AudienceEstimateResponse res = AudienceEstimateResponse.builder()
                .totalMatchingContacts(42)
                .eligibleWindowContacts(30)
                .ineligibleWindowContacts(12)
                .channel(ChannelType.INSTAGRAM)
                .build();

        when(campaignService.estimateAudience(eq(testOrgId), any(AudienceEstimateRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/campaigns/estimate-audience")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalMatchingContacts").value(42))
                .andExpect(jsonPath("$.data.eligibleWindowContacts").value(30))
                .andExpect(jsonPath("$.data.ineligibleWindowContacts").value(12));
    }

    @Test
    @DisplayName("POST /api/v1/campaigns creates a scheduled campaign")
    void testCreateCampaign() throws Exception {
        CreateCampaignRequest req = CreateCampaignRequest.builder()
                .name("Black Friday Teaser")
                .channel(ChannelType.WHATSAPP)
                .messageTemplate("Exclusive BF early bird access!")
                .build();

        CampaignResponse res = CampaignResponse.builder()
                .id(UUID.randomUUID())
                .name("Black Friday Teaser")
                .channel(ChannelType.WHATSAPP)
                .status(BroadcastCampaignStatus.SCHEDULED)
                .totalRecipients(150)
                .createdAt(Instant.now())
                .build();

        when(campaignService.createCampaign(eq(testOrgId), any(CreateCampaignRequest.class))).thenReturn(res);

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Black Friday Teaser"))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.totalRecipients").value(150));
    }

    @Test
    @DisplayName("GET /api/v1/campaigns returns paginated list of campaigns")
    void testGetCampaigns() throws Exception {
        CampaignResponse c1 = CampaignResponse.builder()
                .id(UUID.randomUUID())
                .name("Campaign 1")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.COMPLETED)
                .build();

        when(campaignService.getCampaigns(eq(testOrgId), any(), any()))
                .thenReturn(new PageImpl<>(List.of(c1)));

        mockMvc.perform(get("/api/v1/campaigns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Campaign 1"));
    }

    @Test
    @DisplayName("GET /api/v1/campaigns/{id} returns campaign details and delivery rate")
    void testGetCampaignDetail() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignResponse c = CampaignResponse.builder()
                .id(campaignId)
                .name("Detailed Campaign")
                .channel(ChannelType.INSTAGRAM)
                .status(BroadcastCampaignStatus.COMPLETED)
                .totalRecipients(100)
                .sentCount(85)
                .build();

        CampaignDetailResponse detail = CampaignDetailResponse.builder()
                .campaign(c)
                .recentRecipients(List.of())
                .deliveryRate(85.0)
                .build();

        when(campaignService.getCampaignDetail(eq(testOrgId), eq(campaignId))).thenReturn(detail);

        mockMvc.perform(get("/api/v1/campaigns/{id}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.campaign.name").value("Detailed Campaign"))
                .andExpect(jsonPath("$.data.deliveryRate").value(85.0));
    }

    @Test
    @DisplayName("POST /api/v1/campaigns/{id}/cancel cancels scheduled campaign")
    void testCancelCampaign() throws Exception {
        UUID campaignId = UUID.randomUUID();
        CampaignResponse res = CampaignResponse.builder()
                .id(campaignId)
                .name("Cancelled Campaign")
                .status(BroadcastCampaignStatus.CANCELLED)
                .build();

        when(campaignService.cancelCampaign(eq(testOrgId), eq(campaignId))).thenReturn(res);

        mockMvc.perform(post("/api/v1/campaigns/{id}/cancel", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        verify(campaignService).cancelCampaign(testOrgId, campaignId);
    }
}
