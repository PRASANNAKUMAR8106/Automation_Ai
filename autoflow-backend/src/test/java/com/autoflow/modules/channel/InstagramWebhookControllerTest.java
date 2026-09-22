package com.autoflow.modules.channel;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.channel.controller.InstagramWebhookController;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.autoflow.modules.workflow.engine.InboundEventContext;
import com.autoflow.modules.workflow.engine.WorkflowExecutionEngine;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = InstagramWebhookController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InstagramWebhookController Web MVC Tests")
class InstagramWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConnectedAccountRepository connectedAccountRepository;

    @MockBean
    private WebhookEventRepository webhookEventRepository;

    @MockBean
    private WorkflowExecutionEngine workflowExecutionEngine;

    @MockBean
    private CrmService crmService;

    @MockBean
    private TokenEncryptionService tokenEncryptionService;

    @Test
    @DisplayName("GET /api/v1/webhooks/instagram returns challenge on valid token")
    void testWebhookVerificationSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/instagram")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "autoflow_webhook_verify_token_2026")
                        .param("hub.challenge", "challenge_code_12345"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_code_12345"));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/instagram returns 403 on invalid token")
    void testWebhookVerificationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/instagram")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong_token")
                        .param("hub.challenge", "challenge_code_12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/instagram ingests comment event and dispatches workflow")
    void testIngestCommentEvent() throws Exception {
        String payload = """
                {
                    "object": "instagram",
                    "entry": [
                        {
                            "id": "17841405822304914",
                            "time": 1711000000,
                            "changes": [
                                {
                                    "field": "comments",
                                    "value": {
                                        "id": "comment_123",
                                        "text": "GUIDE please",
                                        "from": {
                                            "id": "user_789",
                                            "username": "sarah_k"
                                        }
                                    }
                                }
                            ]
                        }
                    ]
                }
                """;

        UUID orgId = UUID.randomUUID();
        ConnectedAccount account = ConnectedAccount.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalAccountId("17841405822304914")
                .encryptedAccessToken("enc_token_123")
                .build();
        account.setOrganizationId(orgId);

        Contact contact = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("user_789").build();
        contact.setId(UUID.randomUUID());
        Conversation convo = Conversation.builder().organizationId(orgId).contact(contact).build();

        when(connectedAccountRepository.findByChannelAndExternalAccountId(ChannelType.INSTAGRAM, "17841405822304914"))
                .thenReturn(Optional.of(account));
        when(webhookEventRepository.existsByProviderAndEventId(eq("INSTAGRAM"), anyString())).thenReturn(false);
        when(tokenEncryptionService.decrypt("enc_token_123")).thenReturn("mock_token");
        when(crmService.getOrCreateContact(eq(orgId), eq(ChannelType.INSTAGRAM), eq("user_789"), eq("sarah_k"), any()))
                .thenReturn(contact);
        when(crmService.getOrCreateConversation(eq(orgId), eq(contact))).thenReturn(convo);

        mockMvc.perform(post("/api/v1/webhooks/instagram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(crmService).getOrCreateContact(orgId, ChannelType.INSTAGRAM, "user_789", "sarah_k", null);
        verify(workflowExecutionEngine).evaluateAndExecute(any(InboundEventContext.class));
    }
}
