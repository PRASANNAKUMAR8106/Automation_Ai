package com.autoflow.modules.campaign.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.modules.campaign.dto.CampaignDto.*;
import com.autoflow.modules.campaign.entity.BroadcastCampaign;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import com.autoflow.modules.campaign.entity.BroadcastRecipient;
import com.autoflow.modules.campaign.entity.BroadcastRecipientStatus;
import com.autoflow.modules.campaign.repository.BroadcastCampaignRepository;
import com.autoflow.modules.campaign.repository.BroadcastRecipientRepository;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.provider.instagram.InstagramChannelProvider;
import com.autoflow.modules.channel.provider.telegram.TelegramChannelProvider;
import com.autoflow.modules.channel.provider.whatsapp.WhatsAppChannelProvider;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.dto.CrmDto.MessagingWindowResponse;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.LeadStatus;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.MessagingWindowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignService Unit Tests")
class CampaignServiceTest {

    @Mock
    private BroadcastCampaignRepository campaignRepository;

    @Mock
    private BroadcastRecipientRepository campaignRecipientRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessagingWindowService messagingWindowService;

    @Mock
    private ConnectedAccountRepository connectedAccountRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private InstagramChannelProvider instagramChannelProvider;

    @Mock
    private WhatsAppChannelProvider whatsAppChannelProvider;

    @Mock
    private TelegramChannelProvider telegramChannelProvider;

    private CampaignServiceImpl campaignService;
    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        campaignService = new CampaignServiceImpl(
                campaignRepository,
                campaignRecipientRepository,
                contactRepository,
                conversationRepository,
                messagingWindowService,
                connectedAccountRepository,
                tokenEncryptionService,
                instagramChannelProvider,
                whatsAppChannelProvider,
                telegramChannelProvider
        );
    }

    @Test
    @DisplayName("estimateAudience correctly filters contacts and evaluates window eligibility")
    void shouldEstimateAudienceAccurately() {
        Contact c1 = Contact.builder().channel(ChannelType.INSTAGRAM).leadStatus(LeadStatus.QUALIFIED).leadScore(80).tags(List.of("vip")).build();
        c1.setId(UUID.randomUUID());
        Contact c2 = Contact.builder().channel(ChannelType.INSTAGRAM).leadStatus(LeadStatus.QUALIFIED).leadScore(50).tags(List.of("newsletter")).build();
        c2.setId(UUID.randomUUID());
        Contact c3 = Contact.builder().channel(ChannelType.INSTAGRAM).leadStatus(LeadStatus.NEW).leadScore(10).tags(List.of("vip")).build();
        c3.setId(UUID.randomUUID());

        when(contactRepository.findByOrganizationIdAndChannel(testOrgId, ChannelType.INSTAGRAM))
                .thenReturn(List.of(c1, c2, c3));

        Conversation convo1 = Conversation.builder().channel(ChannelType.INSTAGRAM).build();
        when(conversationRepository.findByOrganizationIdAndContactIdAndChannel(testOrgId, c1.getId(), ChannelType.INSTAGRAM))
                .thenReturn(Optional.of(convo1));
        when(messagingWindowService.evaluateWindow(convo1)).thenReturn(
                MessagingWindowResponse.builder().canSendFreeform(true).build()
        );

        AudienceEstimateRequest req = AudienceEstimateRequest.builder()
                .channel(ChannelType.INSTAGRAM)
                .targetLeadStatus(LeadStatus.QUALIFIED)
                .targetTags(List.of("vip"))
                .minLeadScore(60)
                .build();

        AudienceEstimateResponse response = campaignService.estimateAudience(testOrgId, req);

        assertNotNull(response);
        assertEquals(1, response.getTotalMatchingContacts());
        assertEquals(1, response.getEligibleWindowContacts());
        assertEquals(0, response.getIneligibleWindowContacts());
    }

    @Test
    @DisplayName("createCampaign saves scheduled campaign and resolves recipient batch")
    void shouldCreateScheduledCampaign() {
        CreateCampaignRequest req = CreateCampaignRequest.builder()
                .name("Flash Sale Broadcast")
                .channel(ChannelType.TELEGRAM)
                .messageTemplate("Hey {{name}}! Use code FLASH50 for 50% off.")
                .scheduledAt(Instant.now().plusSeconds(3600))
                .build();

        Contact c1 = Contact.builder().channel(ChannelType.TELEGRAM).username("john_doe").fullName("John Doe").build();
        c1.setId(UUID.randomUUID());

        when(contactRepository.findByOrganizationIdAndChannel(testOrgId, ChannelType.TELEGRAM))
                .thenReturn(List.of(c1));

        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(inv -> {
            BroadcastCampaign c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });

        CampaignResponse response = campaignService.createCampaign(testOrgId, req);

        assertNotNull(response);
        assertEquals("Flash Sale Broadcast", response.getName());
        assertEquals(BroadcastCampaignStatus.SCHEDULED, response.getStatus());
        assertEquals(1, response.getTotalRecipients());
        verify(campaignRecipientRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("getCampaignDetail returns metrics and recent recipient records")
    void shouldReturnCampaignDetailWithDeliveryRate() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Product Launch")
                .channel(ChannelType.WHATSAPP)
                .status(BroadcastCampaignStatus.COMPLETED)
                .totalRecipients(100)
                .sentCount(90)
                .deliveredCount(90)
                .failedCount(10)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        when(campaignRepository.findByIdAndOrganizationId(campaignId, testOrgId)).thenReturn(Optional.of(campaign));
        when(campaignRecipientRepository.findByCampaignId(eq(campaignId), any())).thenReturn(new PageImpl<>(List.of()));

        CampaignDetailResponse detail = campaignService.getCampaignDetail(testOrgId, campaignId);

        assertNotNull(detail);
        assertEquals("Product Launch", detail.getCampaign().getName());
        assertEquals(90.0, detail.getDeliveryRate());
    }

    @Test
    @DisplayName("cancelCampaign updates status to CANCELLED and rejects already completed campaigns")
    void shouldCancelCampaignGracefully() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Pending Launch")
                .channel(ChannelType.INSTAGRAM)
                .status(BroadcastCampaignStatus.SCHEDULED)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        when(campaignRepository.findByIdAndOrganizationId(campaignId, testOrgId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(i -> i.getArgument(0));

        CampaignResponse cancelled = campaignService.cancelCampaign(testOrgId, campaignId);
        assertEquals(BroadcastCampaignStatus.CANCELLED, cancelled.getStatus());

        // Test rejecting cancellation on COMPLETED campaign
        campaign.setStatus(BroadcastCampaignStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> campaignService.cancelCampaign(testOrgId, campaignId));
    }

    @Test
    @DisplayName("executeCampaign personalizes templates, respects 24-hour window, and dispatches messages")
    void shouldExecuteCampaignWithPersonalizationAndWindowCompliance() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("VIP Webinar Invite")
                .channel(ChannelType.INSTAGRAM)
                .status(BroadcastCampaignStatus.SCHEDULED)
                .messageTemplate("Hi {{name}}, welcome to {{channel}} webinar!")
                .skipExpiredWindow(true)
                .totalRecipients(2)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        Contact eligibleContact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_101")
                .fullName("Priya Sharma")
                .username("priyasharma")
                .build();
        eligibleContact.setId(UUID.randomUUID());

        Contact expiredContact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_102")
                .fullName("Devin Vance")
                .username("devinvance")
                .build();
        expiredContact.setId(UUID.randomUUID());

        BroadcastRecipient r1 = BroadcastRecipient.builder().campaign(campaign).contact(eligibleContact).status(BroadcastRecipientStatus.PENDING).build();
        BroadcastRecipient r2 = BroadcastRecipient.builder().campaign(campaign).contact(expiredContact).status(BroadcastRecipientStatus.PENDING).build();

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRecipientRepository.findByCampaignIdAndStatus(campaignId, BroadcastRecipientStatus.PENDING))
                .thenReturn(List.of(r1, r2));

        // Eligible contact conversation within 24h
        Conversation c1 = Conversation.builder().build();
        when(conversationRepository.findByOrganizationIdAndContactIdAndChannel(testOrgId, eligibleContact.getId(), ChannelType.INSTAGRAM))
                .thenReturn(Optional.of(c1));
        when(messagingWindowService.evaluateWindow(c1)).thenReturn(
                MessagingWindowResponse.builder().canSendFreeform(true).build()
        );

        // Expired contact has no active conversation
        when(conversationRepository.findByOrganizationIdAndContactIdAndChannel(testOrgId, expiredContact.getId(), ChannelType.INSTAGRAM))
                .thenReturn(Optional.empty());

        when(instagramChannelProvider.sendPrivateDirectMessage(any(), eq("ig_101"), eq("Hi Priya Sharma, welcome to INSTAGRAM webinar!")))
                .thenReturn("msg_dispatch_999");
        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.executeCampaign(campaignId);

        assertEquals(BroadcastCampaignStatus.COMPLETED, campaign.getStatus());
        assertEquals(1, campaign.getSentCount());
        assertEquals(BroadcastRecipientStatus.SENT, r1.getStatus());
        assertEquals(BroadcastRecipientStatus.SKIPPED_WINDOW, r2.getStatus());

        verify(instagramChannelProvider).sendPrivateDirectMessage("mock_channel_token", "ig_101", "Hi Priya Sharma, welcome to INSTAGRAM webinar!");
        verify(instagramChannelProvider, never()).sendPrivateDirectMessage(any(), eq("ig_102"), any());
    }

    @Test
    @DisplayName("Cross-tenant access throws ResourceNotFoundException")
    void shouldDenyCrossTenantAccess() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndOrganizationId(campaignId, testOrgId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> campaignService.getCampaignDetail(testOrgId, campaignId));
    }
}
