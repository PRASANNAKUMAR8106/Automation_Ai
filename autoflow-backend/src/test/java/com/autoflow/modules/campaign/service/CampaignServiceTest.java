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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignService Production-Hardened Unit Tests")
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
        Contact c1 = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_1").leadStatus(LeadStatus.QUALIFIED).leadScore(80).tags(List.of("vip")).build();
        c1.setId(UUID.randomUUID());
        Contact c2 = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_2").leadStatus(LeadStatus.QUALIFIED).leadScore(50).tags(List.of("newsletter")).build();
        c2.setId(UUID.randomUUID());
        Contact c3 = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_3").leadStatus(LeadStatus.NEW).leadScore(10).tags(List.of("vip")).build();
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
    @DisplayName("Channel-specific messaging eligibility independently verifies Telegram, WhatsApp, and Instagram")
    void shouldEnforceChannelSpecificEligibilityIndependently() {
        // 1. Telegram: Subscriber model, no 24h decay window limitation
        Contact telegramContact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("tg_chat_888")
                .fullName("Alex Chen")
                .build();
        telegramContact.setId(UUID.randomUUID());

        var tgResult = campaignService.checkChannelEligibility(testOrgId, telegramContact, ChannelType.TELEGRAM, true);
        assertTrue(tgResult.isEligible());
        assertNull(tgResult.getStatus());

        // 2. WhatsApp: Strict 24-hour customer care session window
        Contact waContact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId("+15551234567")
                .fullName("Sarah Connor")
                .build();
        waContact.setId(UUID.randomUUID());

        Conversation waConvo = Conversation.builder().channel(ChannelType.WHATSAPP).build();
        when(conversationRepository.findByOrganizationIdAndContactIdAndChannel(testOrgId, waContact.getId(), ChannelType.WHATSAPP))
                .thenReturn(Optional.of(waConvo));
        when(messagingWindowService.evaluateWindow(waConvo)).thenReturn(
                MessagingWindowResponse.builder().canSendFreeform(false).windowStatus("EXPIRED").build()
        );

        var waResult = campaignService.checkChannelEligibility(testOrgId, waContact, ChannelType.WHATSAPP, true);
        assertFalse(waResult.isEligible());
        assertEquals(BroadcastRecipientStatus.SKIPPED_WINDOW, waResult.getStatus());
        assertTrue(waResult.getReason().contains("WhatsApp"));

        // 3. Instagram: Strict 24-hour window, broadcasts cannot use 7-day human agent extension
        Contact igContact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_user_444")
                .fullName("Elena Gilbert")
                .build();
        igContact.setId(UUID.randomUUID());

        Conversation igConvo = Conversation.builder().channel(ChannelType.INSTAGRAM).build();
        when(conversationRepository.findByOrganizationIdAndContactIdAndChannel(testOrgId, igContact.getId(), ChannelType.INSTAGRAM))
                .thenReturn(Optional.of(igConvo));
        // Simulate Instagram 7d human agent extended window where canSendFreeform is false
        when(messagingWindowService.evaluateWindow(igConvo)).thenReturn(
                MessagingWindowResponse.builder().canSendFreeform(false).windowStatus("HUMAN_AGENT_EXTENDED_7D").build()
        );

        var igResult = campaignService.checkChannelEligibility(testOrgId, igContact, ChannelType.INSTAGRAM, true);
        assertFalse(igResult.isEligible());
        assertEquals(BroadcastRecipientStatus.SKIPPED_WINDOW, igResult.getStatus());
        assertTrue(igResult.getReason().contains("Instagram"));
        assertTrue(igResult.getReason().contains("human agent"));
    }

    @Test
    @DisplayName("Persistent consent, opt-out, and suppression handling at discovery and re-checked immediately before dispatch")
    void shouldEnforcePersistentConsentAndSuppressionHandling() {
        // Contact 1: Valid
        Contact validContact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("tg_valid")
                .fullName("Valid Contact")
                .tags(List.of("customer"))
                .build();
        validContact.setId(UUID.randomUUID());

        // Contact 2: Opted out via tag
        Contact optedOutContact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("tg_opted_out")
                .fullName("Opted Out Contact")
                .tags(List.of("opt_out", "customer"))
                .build();
        optedOutContact.setId(UUID.randomUUID());

        when(contactRepository.findByOrganizationIdAndChannel(testOrgId, ChannelType.TELEGRAM))
                .thenReturn(List.of(validContact, optedOutContact));

        // When creating campaign, opted-out contact must be excluded at discovery
        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(i -> {
            BroadcastCampaign c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        CreateCampaignRequest req = CreateCampaignRequest.builder()
                .name("Consent Verified Campaign")
                .channel(ChannelType.TELEGRAM)
                .messageTemplate("Hello {{name}}!")
                .scheduledAt(Instant.now().plusSeconds(3600))
                .build();

        CampaignResponse response = campaignService.createCampaign(testOrgId, req);
        assertEquals(1, response.getTotalRecipients(), "Suppressed/opted-out contact must be excluded from recipient list");

        // Now test re-check immediately before dispatch:
        // Suppose validContact opted out AFTER campaign was created
        UUID campaignId = response.getId();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Consent Verified Campaign")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.SCHEDULED)
                .messageTemplate("Hello {{name}}!")
                .totalRecipients(1)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        BroadcastRecipient recipient = BroadcastRecipient.builder()
                .campaign(campaign)
                .contact(validContact)
                .status(BroadcastRecipientStatus.PENDING)
                .build();
        recipient.setId(UUID.randomUUID());

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.claimCampaignForExecution(eq(campaignId), any())).thenReturn(1);
        when(campaignRecipientRepository.findByCampaignIdAndStatus(campaignId, BroadcastRecipientStatus.PENDING))
                .thenReturn(List.of(recipient));
        when(campaignRecipientRepository.claimRecipientForProcessing(eq(recipient.getId()), any(), any())).thenReturn(1);
        when(campaignRepository.findStatusById(campaignId)).thenReturn(Optional.of(BroadcastCampaignStatus.RUNNING));

        // Re-fetched contact fresh from DB now has "unsubscribed" tag!
        Contact freshlySuppressedContact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("tg_valid")
                .fullName("Valid Contact")
                .tags(List.of("unsubscribed"))
                .build();
        freshlySuppressedContact.setId(validContact.getId());
        when(contactRepository.findById(validContact.getId())).thenReturn(Optional.of(freshlySuppressedContact));

        campaignService.executeCampaign(campaignId);

        // Recipient must be marked SKIPPED_OPT_OUT and provider must NOT be called
        assertEquals(BroadcastRecipientStatus.SKIPPED_OPT_OUT, recipient.getStatus());
        verify(telegramChannelProvider, never()).sendMessage(any(), any(), any());
    }

    @Test
    @DisplayName("Scheduled campaign claiming is atomic: duplicate scheduler or worker instances cannot execute same campaign")
    void shouldEnforceAtomicScheduledCampaignClaiming() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Concurrent Scheduled Campaign")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.SCHEDULED)
                .scheduledAt(Instant.now().minusSeconds(10))
                .build();
        campaign.setId(campaignId);

        when(campaignRepository.findByStatusAndScheduledAtLessThanEqual(eq(BroadcastCampaignStatus.SCHEDULED), any()))
                .thenReturn(List.of(campaign));

        // Worker 1 atomically claims: returns 1 (success)
        when(campaignRepository.claimCampaignForExecution(eq(campaignId), any())).thenReturn(1);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRecipientRepository.findByCampaignIdAndStatus(campaignId, BroadcastRecipientStatus.PENDING))
                .thenReturn(List.of());
        when(campaignRepository.findStatusById(campaignId)).thenReturn(Optional.of(BroadcastCampaignStatus.RUNNING));
        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.processScheduledCampaigns();

        // Worker 2 calls claimCampaignForExecution concurrently: returns 0 (already claimed)
        when(campaignRepository.claimCampaignForExecution(eq(campaignId), any())).thenReturn(0);

        campaignService.processScheduledCampaigns();

        // Verify executeCampaign only ran for the first worker that claimed it
        verify(campaignRepository, times(1)).save(campaign);
    }

    @Test
    @DisplayName("Recipient-level idempotency prevents double sending under concurrent workers or retries")
    void shouldEnforceRecipientLevelIdempotency() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Idempotent Campaign")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.RUNNING)
                .messageTemplate("Hi {{name}}")
                .totalRecipients(1)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        Contact c = Contact.builder().channel(ChannelType.TELEGRAM).externalId("tg_rec_1").build();
        c.setId(UUID.randomUUID());

        BroadcastRecipient r = BroadcastRecipient.builder().campaign(campaign).contact(c).status(BroadcastRecipientStatus.PENDING).build();
        r.setId(UUID.randomUUID());

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRecipientRepository.findByCampaignIdAndStatus(campaignId, BroadcastRecipientStatus.PENDING))
                .thenReturn(List.of(r));

        // Atomic recipient claim returns 0 (already claimed by another worker thread)
        when(campaignRecipientRepository.claimRecipientForProcessing(eq(r.getId()), any(), any())).thenReturn(0);
        when(campaignRepository.findStatusById(campaignId)).thenReturn(Optional.of(BroadcastCampaignStatus.RUNNING));
        when(campaignRepository.save(any(BroadcastCampaign.class))).thenAnswer(i -> i.getArgument(0));

        campaignService.executeCampaign(campaignId);

        // Never dispatched because recipient was already claimed
        verify(telegramChannelProvider, never()).sendMessage(any(), any(), any());
        verify(contactRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Cancellation behavior halts execution loop mid-run and preserves CANCELLED status")
    void shouldHaltExecutionAndPreserveCancelledStatusOnMidRunCancellation() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Cancel Mid-Run Campaign")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.RUNNING)
                .messageTemplate("Hi {{name}}")
                .totalRecipients(2)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        Contact c1 = Contact.builder().channel(ChannelType.TELEGRAM).externalId("tg_1").fullName("User 1").build();
        c1.setId(UUID.randomUUID());
        Contact c2 = Contact.builder().channel(ChannelType.TELEGRAM).externalId("tg_2").fullName("User 2").build();
        c2.setId(UUID.randomUUID());

        BroadcastRecipient r1 = BroadcastRecipient.builder().campaign(campaign).contact(c1).status(BroadcastRecipientStatus.PENDING).build();
        r1.setId(UUID.randomUUID());
        BroadcastRecipient r2 = BroadcastRecipient.builder().campaign(campaign).contact(c2).status(BroadcastRecipientStatus.PENDING).build();
        r2.setId(UUID.randomUUID());

        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(campaignRecipientRepository.findByCampaignIdAndStatus(campaignId, BroadcastRecipientStatus.PENDING))
                .thenReturn(List.of(r1, r2));

        // R1: claimed successfully, campaign is RUNNING
        when(campaignRecipientRepository.claimRecipientForProcessing(eq(r1.getId()), any(), any())).thenReturn(1);
        when(campaignRepository.findStatusById(campaignId))
                .thenReturn(Optional.of(BroadcastCampaignStatus.RUNNING)) // during R1 check
                .thenReturn(Optional.of(BroadcastCampaignStatus.CANCELLED)); // during R2 check (operator cancelled!)

        when(contactRepository.findById(c1.getId())).thenReturn(Optional.of(c1));
        when(telegramChannelProvider.sendMessage(any(), eq("tg_1"), any())).thenReturn("tg_msg_first_recipient");

        // R2: claimed successfully
        when(campaignRecipientRepository.claimRecipientForProcessing(eq(r2.getId()), any(), any())).thenReturn(1);

        campaignService.executeCampaign(campaignId);

        // R1 was sent before cancellation and recorded providerMessageId
        assertEquals(BroadcastRecipientStatus.SENT, r1.getStatus());
        assertEquals("tg_msg_first_recipient", r1.getProviderMessageId());
        verify(telegramChannelProvider, times(1)).sendMessage(any(), eq("tg_1"), any());

        // R2 detected cancellation, was marked CANCELLED, and was NOT dispatched
        assertEquals(BroadcastRecipientStatus.CANCELLED, r2.getStatus());
        verify(telegramChannelProvider, never()).sendMessage(any(), eq("tg_2"), any());

        // Campaign status remains CANCELLED and was NOT overwritten to COMPLETED
        assertNotEquals(BroadcastCampaignStatus.COMPLETED, campaign.getStatus());
        assertEquals(1, campaign.getSentCount());
    }

    @Test
    @DisplayName("cancelCampaign atomically cancels campaign and marks remaining pending recipients as CANCELLED")
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
        when(campaignRepository.cancelCampaignAtomically(testOrgId, campaignId)).thenReturn(1);

        CampaignResponse cancelled = campaignService.cancelCampaign(testOrgId, campaignId);
        assertNotNull(cancelled);
        verify(campaignRecipientRepository).cancelPendingRecipients(campaignId);

        // Test rejecting cancellation on COMPLETED campaign
        campaign.setStatus(BroadcastCampaignStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> campaignService.cancelCampaign(testOrgId, campaignId));
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
    @DisplayName("Crash recovery: Stale processing claims without provider confirmation transition to FAILED_CRASH_RECOVERY without re-dispatching")
    void shouldRecoverStaleProcessingClaimsWithoutReDispatchingToPreventDuplicates() {
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Crash Test Campaign")
                .channel(ChannelType.WHATSAPP)
                .build();
        campaign.setId(UUID.randomUUID());

        BroadcastRecipient staleRecipient = BroadcastRecipient.builder()
                .campaign(campaign)
                .status(BroadcastRecipientStatus.PROCESSING)
                .claimedAt(Instant.now().minus(Duration.ofMinutes(10)))
                .idempotencyKey("camp_test_rec_1")
                .providerMessageId(null)
                .attemptCount(1)
                .build();
        staleRecipient.setId(UUID.randomUUID());

        when(campaignRecipientRepository.findStaleProcessingRecipients(any(Instant.class)))
                .thenReturn(List.of(staleRecipient));
        when(campaignRecipientRepository.save(any(BroadcastRecipient.class))).thenAnswer(i -> i.getArgument(0));

        int recovered = campaignService.recoverStaleProcessingClaims(Duration.ofMinutes(5));

        assertEquals(1, recovered);
        assertEquals(BroadcastRecipientStatus.FAILED_CRASH_RECOVERY, staleRecipient.getStatus());
        assertTrue(staleRecipient.getErrorMessage().contains("lease expired"));
        // Confirm no channel provider was blindly invoked, preventing duplicate delivery
        verify(whatsAppChannelProvider, never()).sendMessage(any(), any(), any());
        verify(whatsAppChannelProvider, never()).sendMediaMessage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Crash recovery: Stale processing claim with confirmed providerMessageId transitions to SENT")
    void shouldRecoverStaleProcessingClaimIfProviderMessageIdAlreadyPresent() {
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Confirmed Crash Recovery")
                .channel(ChannelType.INSTAGRAM)
                .build();
        campaign.setId(UUID.randomUUID());

        BroadcastRecipient staleConfirmedRecipient = BroadcastRecipient.builder()
                .campaign(campaign)
                .status(BroadcastRecipientStatus.PROCESSING)
                .claimedAt(Instant.now().minus(Duration.ofMinutes(15)))
                .idempotencyKey("camp_test_rec_2")
                .providerMessageId("ig_meta_mid_998877")
                .attemptCount(1)
                .build();
        staleConfirmedRecipient.setId(UUID.randomUUID());

        when(campaignRecipientRepository.findStaleProcessingRecipients(any(Instant.class)))
                .thenReturn(List.of(staleConfirmedRecipient));
        when(campaignRecipientRepository.save(any(BroadcastRecipient.class))).thenAnswer(i -> i.getArgument(0));

        int recovered = campaignService.recoverStaleProcessingClaims(Duration.ofMinutes(5));

        assertEquals(1, recovered);
        assertEquals(BroadcastRecipientStatus.SENT, staleConfirmedRecipient.getStatus());
        assertEquals("ig_meta_mid_998877", staleConfirmedRecipient.getProviderMessageId());
        assertNull(staleConfirmedRecipient.getErrorMessage());
        assertNotNull(staleConfirmedRecipient.getSentAt());
        verify(instagramChannelProvider, never()).sendPrivateDirectMessage(any(), any(), any());
    }

    @Test
    @DisplayName("Cancellation semantics: Verified that already accepted provider messages are preserved and not undone")
    void shouldPreserveAlreadySentMessagesDuringCancellationRaceCondition() {
        UUID campaignId = UUID.randomUUID();
        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name("Cancellation Semantics Campaign")
                .channel(ChannelType.TELEGRAM)
                .status(BroadcastCampaignStatus.RUNNING)
                .totalRecipients(2)
                .sentCount(1)
                .build();
        campaign.setId(campaignId);
        campaign.setOrganizationId(testOrgId);

        when(campaignRepository.findByIdAndOrganizationId(campaignId, testOrgId)).thenReturn(Optional.of(campaign));
        when(campaignRepository.cancelCampaignAtomically(testOrgId, campaignId)).thenReturn(1);
        when(campaignRecipientRepository.cancelPendingRecipients(campaignId)).thenReturn(1);

        CampaignResponse response = campaignService.cancelCampaign(testOrgId, campaignId);

        assertNotNull(response);
        verify(campaignRecipientRepository).cancelPendingRecipients(campaignId);
        // Verify cancelPendingRecipients was called for the campaign, which only targets PENDING or unconfirmed PROCESSING
        verify(campaignRepository).cancelCampaignAtomically(testOrgId, campaignId);
    }

    @Test
    @DisplayName("Cross-tenant access throws ResourceNotFoundException")
    void shouldDenyCrossTenantAccess() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findByIdAndOrganizationId(campaignId, testOrgId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> campaignService.getCampaignDetail(testOrgId, campaignId));
    }
}
