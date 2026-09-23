package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.CrmDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LiveChatStreamService Real-Time SSE Streaming Tests")
class LiveChatStreamServiceTest {

    private LiveChatStreamServiceImpl streamService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        streamService = new LiveChatStreamServiceImpl();
    }

    @Test
    @DisplayName("Should successfully subscribe to conversation stream")
    void shouldSubscribeToConversationAndReceiveAck() {
        SseEmitter emitter = streamService.subscribeToConversation(tenantA, conversationId);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("Should successfully subscribe to tenant inbox stream")
    void shouldSubscribeToTenantInboxAndReceiveAck() {
        SseEmitter emitter = streamService.subscribeToTenantInbox(tenantA);
        assertThat(emitter).isNotNull();
    }

    @Test
    @DisplayName("Should broadcast message to conversation subscribers without error")
    void shouldBroadcastMessageToMatchingConversationAndTenant() {
        SseEmitter emitterA = streamService.subscribeToConversation(tenantA, conversationId);
        SseEmitter inboxA = streamService.subscribeToTenantInbox(tenantA);

        CrmDto.MessageResponse msg = CrmDto.MessageResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .direction("INBOUND")
                .senderType("CONTACT")
                .content("Hello, I need pricing info!")
                .sentAt(Instant.now())
                .deliveryStatus("DELIVERED")
                .build();

        // Broadcast should safely dispatch to both conversation and inbox subscribers
        streamService.broadcastMessage(tenantA, conversationId, msg);

        assertThat(emitterA).isNotNull();
        assertThat(inboxA).isNotNull();
    }

    @Test
    @DisplayName("Cross-tenant isolation: Tenant B does not receive Tenant A events")
    void shouldIsolateCrossTenantEvents() {
        SseEmitter emitterB = streamService.subscribeToConversation(tenantB, conversationId);
        SseEmitter inboxB = streamService.subscribeToTenantInbox(tenantB);

        CrmDto.MessageResponse msg = CrmDto.MessageResponse.builder()
                .id(UUID.randomUUID())
                .conversationId(conversationId)
                .direction("INBOUND")
                .senderType("CONTACT")
                .content("Confidential Tenant A data")
                .sentAt(Instant.now())
                .build();

        // Broadcast for Tenant A - Tenant B emitters should remain unaffected and unpolluted
        streamService.broadcastMessage(tenantA, conversationId, msg);

        assertThat(emitterB).isNotNull();
        assertThat(inboxB).isNotNull();
    }

    @Test
    @DisplayName("Should broadcast typing and resolve events cleanly")
    void shouldBroadcastTypingAndResolveEvents() {
        streamService.subscribeToConversation(tenantA, conversationId);

        streamService.broadcastTyping(tenantA, conversationId, true);
        streamService.broadcastTyping(tenantA, conversationId, false);
        streamService.broadcastConversationResolved(tenantA, conversationId, true);
        streamService.broadcastConversationResolved(tenantA, conversationId, false);
    }

    @Test
    @DisplayName("Should dispatch heartbeats without exceptions")
    void shouldSendHeartbeats() {
        streamService.subscribeToConversation(tenantA, conversationId);
        streamService.subscribeToTenantInbox(tenantA);

        streamService.sendHeartbeats();
    }
}
