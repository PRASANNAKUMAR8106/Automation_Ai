package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.repository.ConversationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Meta & WhatsApp 24-Hour Messaging Window Compliance Service Tests")
class MessagingWindowServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private MessagingWindowServiceImpl messagingWindowService;

    private final UUID orgId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    @DisplayName("Telegram channel is UNRESTRICTED with no 24-hour limit")
    void shouldEvaluateTelegramAsUnrestricted() {
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.TELEGRAM)
                .organizationId(orgId)
                .lastCustomerMessageAt(Instant.now().minus(Duration.ofDays(10)))
                .build();
        conversation.setId(conversationId);

        CrmDto.MessagingWindowResponse response = messagingWindowService.evaluateWindow(conversation);

        assertThat(response.getWindowStatus()).isEqualTo("UNRESTRICTED");
        assertThat(response.getRemainingSeconds()).isNull();
        assertThat(response.isCanSendFreeform()).isTrue();
        assertThat(response.isCanSendHumanAgent()).isTrue();
        assertThat(messagingWindowService.isMessageAllowed(conversation, false)).isTrue();
    }

    @Test
    @DisplayName("Instagram within 24 hours is ACTIVE_24H and allows freeform messaging")
    void shouldEvaluateInstagramWithin24hAsActive24h() {
        Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.INSTAGRAM)
                .organizationId(orgId)
                .lastCustomerMessageAt(twoHoursAgo)
                .build();
        conversation.setId(conversationId);

        CrmDto.MessagingWindowResponse response = messagingWindowService.evaluateWindow(conversation);

        assertThat(response.getWindowStatus()).isEqualTo("ACTIVE_24H");
        assertThat(response.getRemainingSeconds()).isGreaterThan(21 * 3600L);
        assertThat(response.getRemainingSeconds()).isLessThanOrEqualTo(22 * 3600L + 5L);
        assertThat(response.isCanSendFreeform()).isTrue();
        assertThat(response.isCanSendHumanAgent()).isTrue();
        assertThat(messagingWindowService.isMessageAllowed(conversation, false)).isTrue();
    }

    @Test
    @DisplayName("Instagram between 24h and 7d is HUMAN_AGENT_EXTENDED_7D allowing only Human Agent tag")
    void shouldEvaluateInstagramBetween24hAnd7dAsHumanAgentExtended() {
        Instant twoDaysAgo = Instant.now().minus(Duration.ofDays(2));
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.INSTAGRAM)
                .organizationId(orgId)
                .lastCustomerMessageAt(twoDaysAgo)
                .build();
        conversation.setId(conversationId);

        CrmDto.MessagingWindowResponse response = messagingWindowService.evaluateWindow(conversation);

        assertThat(response.getWindowStatus()).isEqualTo("HUMAN_AGENT_EXTENDED_7D");
        assertThat(response.getRemainingSeconds()).isGreaterThan(4 * 24 * 3600L);
        assertThat(response.isCanSendFreeform()).isFalse();
        assertThat(response.isCanSendHumanAgent()).isTrue();

        // Freeform not allowed without tag
        assertThat(messagingWindowService.isMessageAllowed(conversation, false)).isFalse();
        // Allowed with human agent tag
        assertThat(messagingWindowService.isMessageAllowed(conversation, true)).isTrue();
    }

    @Test
    @DisplayName("Instagram beyond 7 days is EXPIRED and prohibits messaging")
    void shouldEvaluateInstagramBeyond7dAsExpired() {
        Instant eightDaysAgo = Instant.now().minus(Duration.ofDays(8));
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.INSTAGRAM)
                .organizationId(orgId)
                .lastCustomerMessageAt(eightDaysAgo)
                .build();
        conversation.setId(conversationId);

        CrmDto.MessagingWindowResponse response = messagingWindowService.evaluateWindow(conversation);

        assertThat(response.getWindowStatus()).isEqualTo("EXPIRED");
        assertThat(response.getRemainingSeconds()).isEqualTo(0L);
        assertThat(response.isCanSendFreeform()).isFalse();
        assertThat(response.isCanSendHumanAgent()).isFalse();

        assertThat(messagingWindowService.isMessageAllowed(conversation, false)).isFalse();
        assertThat(messagingWindowService.isMessageAllowed(conversation, true)).isFalse();
        assertThrows(IllegalStateException.class, () -> messagingWindowService.validateCanSend(conversation, false));
    }

    @Test
    @DisplayName("WhatsApp beyond 24 hours is EXPIRED (does not support 7-day human agent extension)")
    void shouldEvaluateWhatsAppBeyond24hAsExpired() {
        Instant thirtyHoursAgo = Instant.now().minus(Duration.ofHours(30));
        Conversation conversation = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .organizationId(orgId)
                .lastCustomerMessageAt(thirtyHoursAgo)
                .build();
        conversation.setId(conversationId);

        CrmDto.MessagingWindowResponse response = messagingWindowService.evaluateWindow(conversation);

        assertThat(response.getWindowStatus()).isEqualTo("EXPIRED");
        assertThat(response.isCanSendFreeform()).isFalse();
        assertThat(response.isCanSendHumanAgent()).isFalse();
        assertThat(messagingWindowService.isMessageAllowed(conversation, true)).isFalse();
    }

    @Test
    @DisplayName("Cross-tenant lookup throws ResourceNotFoundException")
    void shouldThrowResourceNotFoundExceptionForMissingOrUnauthorizedTenant() {
        UUID unauthorizedOrg = UUID.randomUUID();
        when(conversationRepository.findByIdAndOrganizationId(conversationId, unauthorizedOrg))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> messagingWindowService.getWindowStatus(unauthorizedOrg, conversationId));
    }
}
