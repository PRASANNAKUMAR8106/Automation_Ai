package com.autoflow.modules.channel;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.channel.controller.WhatsAppWebhookController;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.autoflow.modules.workflow.engine.InboundEventContext;
import com.autoflow.modules.workflow.engine.WorkflowExecutionEngine;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WhatsAppWebhookController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WhatsAppWebhookController Web MVC Tests")
class WhatsAppWebhookControllerTest {

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
    @DisplayName("GET /api/v1/webhooks/whatsapp validates Meta hub challenge with 200 OK")
    void testWebhookChallengeVerificationSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "autoflow_webhook_verify_token_2026")
                        .param("hub.challenge", "challenge_code_98765"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_code_98765"));
    }

    @Test
    @DisplayName("GET /api/v1/webhooks/whatsapp rejects invalid verify token with 403 Forbidden")
    void testWebhookChallengeVerificationFailure() throws Exception {
        mockMvc.perform(get("/api/v1/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong_token")
                        .param("hub.challenge", "challenge_code_98765"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/whatsapp processes incoming message, updates CRM, and triggers workflow")
    void testHandleInboundWhatsAppMessage() throws Exception {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "id": "waba_id_100",
                      "changes": [
                        {
                          "value": {
                            "messaging_product": "whatsapp",
                            "metadata": {
                              "display_phone_number": "15550100",
                              "phone_number_id": "phone_id_999"
                            },
                            "contacts": [
                              {
                                "profile": {
                                  "name": "Rohan Sharma"
                                },
                                "wa_id": "919876543210"
                              }
                            ],
                            "messages": [
                              {
                                "from": "919876543210",
                                "id": "wamid.HBgL...",
                                "timestamp": "1711111111",
                                "text": {
                                  "body": "DEMO"
                                },
                                "type": "text"
                              }
                            ]
                          },
                          "field": "messages"
                        }
                      ]
                    }
                  ]
                }
                """;

        when(webhookEventRepository.existsByProviderAndEventId(eq("WHATSAPP"), any())).thenReturn(false);
        when(connectedAccountRepository.findByChannelAndExternalAccountId(eq(ChannelType.WHATSAPP), eq("phone_id_999")))
                .thenReturn(Optional.empty()); // Uses fallback org

        Contact mockContact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId("919876543210")
                .fullName("Rohan Sharma")
                .build();
        mockContact.setId(UUID.randomUUID());

        Conversation mockConvo = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .contact(mockContact)
                .build();
        mockConvo.setId(UUID.randomUUID());

        when(crmService.getOrCreateContact(any(), eq(ChannelType.WHATSAPP), eq("919876543210"), eq("919876543210"), eq("Rohan Sharma")))
                .thenReturn(mockContact);
        when(crmService.getOrCreateConversation(any(), eq(mockContact)))
                .thenReturn(mockConvo);

        mockMvc.perform(post("/api/v1/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(crmService).getOrCreateContact(any(), eq(ChannelType.WHATSAPP), eq("919876543210"), eq("919876543210"), eq("Rohan Sharma"));
        verify(crmService).recordMessage(any(), eq(mockConvo), eq("INBOUND"), eq("CONTACT"), eq("TEXT"), eq("DEMO"), isNull(), eq("wamid.HBgL..."));
        verify(workflowExecutionEngine).evaluateAndExecute(any(InboundEventContext.class));
    }
}
