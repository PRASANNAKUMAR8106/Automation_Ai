package com.autoflow.modules.channel;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.channel.controller.TelegramWebhookController;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = TelegramWebhookController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("TelegramWebhookController Web MVC Tests")
class TelegramWebhookControllerTest {

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
    @DisplayName("POST /api/v1/webhooks/telegram processes incoming update, registers contact, and triggers workflow")
    void testHandleInboundTelegramMessage() throws Exception {
        String payload = """
                {
                  "update_id": 987654321,
                  "message": {
                    "message_id": 101,
                    "from": {
                      "id": 5551234,
                      "is_bot": false,
                      "first_name": "Rohan",
                      "last_name": "Verma",
                      "username": "rohan_v"
                    },
                    "chat": {
                      "id": 5551234,
                      "first_name": "Rohan",
                      "last_name": "Verma",
                      "username": "rohan_v",
                      "type": "private"
                    },
                    "date": 1695470000,
                    "text": "Hello AutoFlow! Send me the promo code."
                  }
                }
                """;

        when(webhookEventRepository.existsByProviderAndEventId("TELEGRAM", "tg_update_987654321")).thenReturn(false);

        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Contact mockContact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("5551234")
                .username("rohan_v")
                .fullName("Rohan Verma")
                .build();
        mockContact.setId(UUID.randomUUID());
        mockContact.setOrganizationId(orgId);

        Conversation mockConversation = Conversation.builder()
                .organizationId(orgId)
                .channel(ChannelType.TELEGRAM)
                .contact(mockContact)
                .build();
        mockConversation.setId(UUID.randomUUID());

        when(crmService.getOrCreateContact(eq(orgId), eq(ChannelType.TELEGRAM), eq("5551234"), eq("rohan_v"), eq("Rohan Verma")))
                .thenReturn(mockContact);
        when(crmService.getOrCreateConversation(eq(orgId), eq(mockContact)))
                .thenReturn(mockConversation);

        mockMvc.perform(post("/api/v1/webhooks/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "autoflow_telegram_secret_2026")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(webhookEventRepository).save(any());
        verify(crmService).getOrCreateContact(eq(orgId), eq(ChannelType.TELEGRAM), eq("5551234"), eq("rohan_v"), eq("Rohan Verma"));
        verify(crmService).recordMessage(
                eq(orgId),
                eq(mockConversation),
                eq("INBOUND"),
                eq("CONTACT"),
                eq("TEXT"),
                eq("Hello AutoFlow! Send me the promo code."),
                isNull(),
                eq("tg_msg_101")
        );
        verify(workflowExecutionEngine).evaluateAndExecute(any(InboundEventContext.class));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/telegram ignores duplicate updates idempotently")
    void testHandleDuplicateTelegramUpdate() throws Exception {
        String payload = """
                {
                  "update_id": 987654321,
                  "message": {
                    "message_id": 101,
                    "from": {"id": 5551234, "first_name": "Rohan"},
                    "chat": {"id": 5551234},
                    "text": "Repeat message"
                  }
                }
                """;

        when(webhookEventRepository.existsByProviderAndEventId("TELEGRAM", "tg_update_987654321")).thenReturn(true);

        mockMvc.perform(post("/api/v1/webhooks/telegram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Telegram-Bot-Api-Secret-Token", "autoflow_telegram_secret_2026")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string("ALREADY_PROCESSED"));

        verify(crmService, never()).getOrCreateContact(any(), any(), any(), any(), any());
        verify(workflowExecutionEngine, never()).evaluateAndExecute(any());
    }
}
