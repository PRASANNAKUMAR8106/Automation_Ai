package com.autoflow.modules.channel;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.channel.controller.ChannelConnectionController;
import com.autoflow.modules.channel.dto.ConnectedAccountResponse;
import com.autoflow.modules.channel.dto.OAuthCallbackRequest;
import com.autoflow.modules.channel.dto.OAuthInitiateResponse;
import com.autoflow.modules.channel.entity.ConnectedAccountStatus;
import com.autoflow.modules.channel.service.ChannelConnectionService;
import com.autoflow.modules.crm.entity.ChannelType;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ChannelConnectionController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ChannelConnectionController Web MVC Tests")
class ChannelConnectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ChannelConnectionService channelConnectionService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID testAccountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/channels/oauth/connect/INSTAGRAM generates OAuth URL")
    void testInitiateOAuth() throws Exception {
        OAuthInitiateResponse response = OAuthInitiateResponse.builder()
                .authorizationUrl("https://facebook.com/dialog/oauth?client_id=123")
                .state("enc_state_nonce")
                .channel(ChannelType.INSTAGRAM)
                .build();

        when(channelConnectionService.initiateOAuth(eq(ChannelType.INSTAGRAM), eq(testOrgId), any(), any()))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/channels/oauth/connect/INSTAGRAM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channel").value("INSTAGRAM"))
                .andExpect(jsonPath("$.data.authorizationUrl").value("https://facebook.com/dialog/oauth?client_id=123"))
                .andExpect(jsonPath("$.data.state").value("enc_state_nonce"));
    }

    @Test
    @DisplayName("POST /api/v1/channels/oauth/callback/INSTAGRAM saves accounts and returns list")
    void testHandleOAuthCallback() throws Exception {
        OAuthCallbackRequest request = OAuthCallbackRequest.builder()
                .code("sample_auth_code")
                .state("enc_state_nonce")
                .build();

        ConnectedAccountResponse accountResponse = ConnectedAccountResponse.builder()
                .id(testAccountId)
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .externalAccountId("17841405822304914")
                .accountName("AutoFlow Official")
                .accountHandle("@autoflow_ai")
                .status(ConnectedAccountStatus.ACTIVE)
                .tokenExpiresAt(Instant.now().plusSeconds(5184000L))
                .build();

        when(channelConnectionService.handleOAuthCallback(eq(ChannelType.INSTAGRAM), any(OAuthCallbackRequest.class)))
                .thenReturn(List.of(accountResponse));

        mockMvc.perform(post("/api/v1/channels/oauth/callback/INSTAGRAM")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].accountHandle").value("@autoflow_ai"))
                .andExpect(jsonPath("$.data[0].channel").value("INSTAGRAM"));
    }

    @Test
    @DisplayName("GET /api/v1/channels/connected returns active accounts for tenant")
    void testGetConnectedAccounts() throws Exception {
        ConnectedAccountResponse accountResponse = ConnectedAccountResponse.builder()
                .id(testAccountId)
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .accountHandle("@autoflow_ai")
                .status(ConnectedAccountStatus.ACTIVE)
                .build();

        when(channelConnectionService.getConnectedAccounts(testOrgId))
                .thenReturn(List.of(accountResponse));

        mockMvc.perform(get("/api/v1/channels/connected"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(testAccountId.toString()))
                .andExpect(jsonPath("$.data[0].accountHandle").value("@autoflow_ai"));
    }

    @Test
    @DisplayName("POST /api/v1/channels/connected/{id}/refresh returns refreshed credentials")
    void testRefreshAccount() throws Exception {
        ConnectedAccountResponse response = ConnectedAccountResponse.builder()
                .id(testAccountId)
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .accountHandle("@autoflow_ai")
                .status(ConnectedAccountStatus.ACTIVE)
                .tokenExpiresAt(Instant.now().plusSeconds(5184000L))
                .build();

        when(channelConnectionService.refreshAccountToken(testAccountId, testOrgId))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/channels/connected/" + testAccountId + "/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(testAccountId.toString()));
    }

    @Test
    @DisplayName("DELETE /api/v1/channels/connected/{id} disconnects account")
    void testDisconnectAccount() throws Exception {
        mockMvc.perform(delete("/api/v1/channels/connected/" + testAccountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(channelConnectionService).disconnectAccount(testAccountId, testOrgId);
    }
}
