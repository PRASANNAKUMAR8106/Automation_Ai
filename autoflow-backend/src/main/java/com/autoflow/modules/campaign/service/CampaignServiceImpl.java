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
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.LeadStatus;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.MessagingWindowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private final BroadcastCampaignRepository campaignRepository;
    private final BroadcastRecipientRepository campaignRecipientRepository;
    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessagingWindowService messagingWindowService;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final InstagramChannelProvider instagramChannelProvider;
    private final WhatsAppChannelProvider whatsAppChannelProvider;
    private final TelegramChannelProvider telegramChannelProvider;

    @Override
    @Transactional(readOnly = true)
    public AudienceEstimateResponse estimateAudience(UUID organizationId, AudienceEstimateRequest request) {
        List<Contact> matching = getMatchingContacts(organizationId, request.getChannel(), request.getTargetTags(), request.getTargetLeadStatus(), request.getMinLeadScore());

        int eligible = 0;
        int ineligible = 0;

        for (Contact c : matching) {
            EligibilityResult res = checkChannelEligibility(organizationId, c, request.getChannel(), true);
            if (res.isEligible()) {
                eligible++;
            } else {
                ineligible++;
            }
        }

        return AudienceEstimateResponse.builder()
                .totalMatchingContacts(matching.size())
                .eligibleWindowContacts(eligible)
                .ineligibleWindowContacts(ineligible)
                .channel(request.getChannel())
                .build();
    }

    @Override
    @Transactional
    public CampaignResponse createCampaign(UUID organizationId, CreateCampaignRequest request) {
        Instant scheduledAt = request.getScheduledAt() != null ? request.getScheduledAt() : Instant.now();

        BroadcastCampaign campaign = BroadcastCampaign.builder()
                .name(request.getName())
                .channel(request.getChannel())
                .status(BroadcastCampaignStatus.SCHEDULED)
                .messageTemplate(request.getMessageTemplate())
                .mediaUrl(request.getMediaUrl())
                .targetTags(request.getTargetTags() != null ? request.getTargetTags() : new ArrayList<>())
                .targetLeadStatus(request.getTargetLeadStatus())
                .minLeadScore(request.getMinLeadScore())
                .skipExpiredWindow(request.isSkipExpiredWindow())
                .scheduledAt(scheduledAt)
                .totalRecipients(0)
                .sentCount(0)
                .deliveredCount(0)
                .failedCount(0)
                .build();
        campaign.setOrganizationId(organizationId);

        BroadcastCampaign savedCampaign = campaignRepository.save(campaign);

        // Fetch matching recipients (already filters out opted-out / suppressed contacts)
        List<Contact> matchingContacts = getMatchingContacts(
                organizationId,
                request.getChannel(),
                request.getTargetTags(),
                request.getTargetLeadStatus(),
                request.getMinLeadScore()
        );

        List<BroadcastRecipient> recipients = new ArrayList<>();
        for (Contact contact : matchingContacts) {
            BroadcastRecipient recipient = BroadcastRecipient.builder()
                    .campaign(savedCampaign)
                    .contact(contact)
                    .status(BroadcastRecipientStatus.PENDING)
                    .build();
            recipients.add(recipient);
        }

        if (!recipients.isEmpty()) {
            campaignRecipientRepository.saveAll(recipients);
        }

        savedCampaign.setTotalRecipients(recipients.size());
        savedCampaign = campaignRepository.save(savedCampaign);

        log.info("Created scheduled campaign {} ({}) with {} recipients for org {}",
                savedCampaign.getId(), savedCampaign.getName(), savedCampaign.getTotalRecipients(), organizationId);

        // If scheduled immediately, execute asynchronously
        if (scheduledAt.isBefore(Instant.now().plusSeconds(2))) {
            executeCampaign(savedCampaign.getId());
        }

        return toCampaignResponse(savedCampaign);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CampaignResponse> getCampaigns(UUID organizationId, BroadcastCampaignStatus status, Pageable pageable) {
        Page<BroadcastCampaign> page = (status != null)
                ? campaignRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status, pageable)
                : campaignRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId, pageable);
        return page.map(this::toCampaignResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CampaignDetailResponse getCampaignDetail(UUID organizationId, UUID campaignId) {
        BroadcastCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        Page<BroadcastRecipient> recipientsPage = campaignRecipientRepository.findByCampaignId(campaignId, PageRequest.of(0, 50));
        List<CampaignRecipientResponse> recipientResponses = recipientsPage.getContent().stream()
                .map(this::toCampaignRecipientResponse)
                .toList();

        double deliveryRate = campaign.getTotalRecipients() > 0
                ? ((double) (campaign.getDeliveredCount() > 0 ? campaign.getDeliveredCount() : campaign.getSentCount()) / campaign.getTotalRecipients()) * 100.0
                : 0.0;

        return CampaignDetailResponse.builder()
                .campaign(toCampaignResponse(campaign))
                .recentRecipients(recipientResponses)
                .deliveryRate(Math.round(deliveryRate * 10.0) / 10.0)
                .build();
    }

    @Override
    @Transactional
    public CampaignResponse cancelCampaign(UUID organizationId, UUID campaignId) {
        BroadcastCampaign campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        if (campaign.getStatus() == BroadcastCampaignStatus.COMPLETED || campaign.getStatus() == BroadcastCampaignStatus.FAILED) {
            throw new IllegalStateException("Cannot cancel a campaign that is already " + campaign.getStatus());
        }

        // Atomically cancel campaign
        campaignRepository.cancelCampaignAtomically(organizationId, campaignId);

        // Atomically cancel any remaining pending/processing recipients
        campaignRecipientRepository.cancelPendingRecipients(campaignId);

        campaign = campaignRepository.findByIdAndOrganizationId(campaignId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Campaign", campaignId));

        log.info("Cancelled campaign {} and marked remaining recipients CANCELLED for org {}", campaignId, organizationId);
        return toCampaignResponse(campaign);
    }

    @Override
    @Transactional
    public void processScheduledCampaigns() {
        List<BroadcastCampaign> dueCampaigns = campaignRepository.findByStatusAndScheduledAtLessThanEqual(
                BroadcastCampaignStatus.SCHEDULED,
                Instant.now()
        );

        for (BroadcastCampaign campaign : dueCampaigns) {
            // Atomic claim prevents multiple scheduler instances from picking up the same campaign
            int claimed = campaignRepository.claimCampaignForExecution(campaign.getId(), Instant.now());
            if (claimed > 0) {
                try {
                    executeCampaign(campaign.getId());
                } catch (Exception e) {
                    log.error("Failed to trigger scheduled campaign {}: {}", campaign.getId(), e.getMessage(), e);
                }
            } else {
                log.debug("Campaign {} already claimed by another scheduler instance.", campaign.getId());
            }
        }
    }

    @Async
    @Override
    @Transactional
    public void executeCampaign(UUID campaignId) {
        Optional<BroadcastCampaign> campaignOpt = campaignRepository.findById(campaignId);
        if (campaignOpt.isEmpty()) return;

        BroadcastCampaign campaign = campaignOpt.get();
        if (campaign.getStatus() == BroadcastCampaignStatus.CANCELLED || campaign.getStatus() == BroadcastCampaignStatus.COMPLETED) {
            return;
        }

        // If not already in RUNNING status, claim it atomically
        if (campaign.getStatus() == BroadcastCampaignStatus.SCHEDULED) {
            int claimed = campaignRepository.claimCampaignForExecution(campaignId, Instant.now());
            if (claimed == 0) {
                log.info("Campaign {} was already claimed by another worker instance.", campaignId);
                return;
            }
            campaign = campaignRepository.findById(campaignId).orElse(campaign);
        }

        log.info("Executing broadcast campaign {} ({}) on channel {}", campaign.getId(), campaign.getName(), campaign.getChannel());

        List<BroadcastRecipient> pendingRecipients = campaignRecipientRepository.findByCampaignIdAndStatus(
                campaign.getId(),
                BroadcastRecipientStatus.PENDING
        );

        String token = resolveChannelToken(campaign.getOrganizationId(), campaign.getChannel());

        int sent = 0;
        int failed = 0;

        for (BroadcastRecipient recipient : pendingRecipients) {
            // 1. Recipient-level atomic claiming with lease timestamp and idempotency key
            String idempotencyKey = "camp_" + campaignId + "_" + recipient.getId();
            Instant claimTime = Instant.now();
            int claimed = campaignRecipientRepository.claimRecipientForProcessing(recipient.getId(), claimTime, idempotencyKey);
            if (claimed == 0) {
                log.debug("Recipient {} already claimed/processed by another thread.", recipient.getId());
                continue;
            }
            recipient.setStatus(BroadcastRecipientStatus.PROCESSING);
            recipient.setIdempotencyKey(idempotencyKey);
            recipient.setClaimedAt(claimTime);
            recipient.setAttemptCount(recipient.getAttemptCount() + 1);

            // 2. Cancellation check: if campaign was cancelled during processing, halt immediately
            Optional<BroadcastCampaignStatus> currentCampaignStatus = campaignRepository.findStatusById(campaignId);
            if (currentCampaignStatus.isEmpty() || currentCampaignStatus.get() == BroadcastCampaignStatus.CANCELLED) {
                recipient.setStatus(BroadcastRecipientStatus.CANCELLED);
                recipient.setErrorMessage("Campaign was cancelled during execution.");
                campaignRecipientRepository.save(recipient);
                log.info("Campaign {} was cancelled mid-run. Aborting recipient loop.", campaignId);
                break;
            }

            // 3. Re-fetch Contact fresh from DB immediately before dispatch (persistent consent & suppression check)
            Contact contact = contactRepository.findById(recipient.getContact().getId()).orElse(null);
            if (contact == null) {
                recipient.setStatus(BroadcastRecipientStatus.FAILED);
                recipient.setErrorMessage("Contact no longer exists in database.");
                campaignRecipientRepository.save(recipient);
                failed++;
                continue;
            }

            // 4. Verify channel eligibility and opt-out / suppression
            EligibilityResult eligibility = checkChannelEligibility(
                    campaign.getOrganizationId(),
                    contact,
                    campaign.getChannel(),
                    campaign.isSkipExpiredWindow()
            );

            if (!eligibility.isEligible()) {
                recipient.setStatus(eligibility.getStatus());
                recipient.setErrorMessage(eligibility.getReason());
                campaignRecipientRepository.save(recipient);
                continue;
            }

            // 5. Personalize template variables
            String personalizedMessage = personalize(campaign.getMessageTemplate(), contact, campaign.getChannel());

            // 6. Dispatch to Channel Provider with external message ID recording
            try {
                String providerMessageId = dispatchMessage(campaign.getChannel(), token, contact.getExternalId(), personalizedMessage, campaign.getMediaUrl());
                recipient.setProviderMessageId(providerMessageId);
                recipient.setStatus(BroadcastRecipientStatus.SENT);
                recipient.setSentAt(Instant.now());
                recipient.setErrorMessage(null);
                sent++;
            } catch (Exception e) {
                log.warn("Failed to dispatch campaign message to recipient {}: {}", contact.getExternalId(), e.getMessage());
                recipient.setStatus(BroadcastRecipientStatus.FAILED);
                recipient.setErrorMessage(e.getMessage());
                failed++;
            }
            campaignRecipientRepository.save(recipient);
        }

        // Verify campaign was not cancelled while processing
        Optional<BroadcastCampaignStatus> finalStatus = campaignRepository.findStatusById(campaignId);
        if (finalStatus.isPresent() && finalStatus.get() == BroadcastCampaignStatus.CANCELLED) {
            log.info("Campaign {} was cancelled during execution. Preserving CANCELLED state and updating final dispatch counts.", campaignId);
            campaign = campaignRepository.findById(campaignId).orElse(campaign);
            campaign.setSentCount(campaign.getSentCount() + sent);
            campaign.setDeliveredCount(campaign.getDeliveredCount() + sent);
            campaign.setFailedCount(campaign.getFailedCount() + failed);
            campaignRepository.save(campaign);
            return;
        }

        campaign = campaignRepository.findById(campaignId).orElse(campaign);
        campaign.setSentCount(campaign.getSentCount() + sent);
        campaign.setDeliveredCount(campaign.getDeliveredCount() + sent);
        campaign.setFailedCount(campaign.getFailedCount() + failed);
        campaign.setCompletedAt(Instant.now());
        campaign.setStatus(BroadcastCampaignStatus.COMPLETED);
        campaignRepository.save(campaign);

        log.info("Completed campaign {}: {} sent, {} failed, {} remaining/skipped",
                campaign.getId(), sent, failed, campaign.getTotalRecipients() - (sent + failed));
    }

    private List<Contact> getMatchingContacts(UUID organizationId, ChannelType channel, List<String> tags, LeadStatus leadStatus, int minLeadScore) {
        List<Contact> all = contactRepository.findByOrganizationIdAndChannel(organizationId, channel);
        return all.stream()
                .filter(c -> {
                    // Persistent opt-out / suppression check at discovery
                    if (c.isSuppressed()) {
                        return false;
                    }
                    if (c.getExternalId() == null || c.getExternalId().trim().isEmpty()) {
                        return false;
                    }
                    if (leadStatus != null && c.getLeadStatus() != leadStatus) {
                        return false;
                    }
                    if (minLeadScore > 0 && c.getLeadScore() < minLeadScore) {
                        return false;
                    }
                    if (tags != null && !tags.isEmpty()) {
                        if (c.getTags() == null || c.getTags().stream().noneMatch(tags::contains)) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();
    }

    /**
     * Verifies channel-specific messaging eligibility independently for Instagram, WhatsApp, and Telegram.
     */
    public EligibilityResult checkChannelEligibility(UUID organizationId, Contact contact, ChannelType channel, boolean skipExpiredWindow) {
        if (contact == null || contact.getExternalId() == null || contact.getExternalId().trim().isEmpty()) {
            return EligibilityResult.ineligible("Missing valid channel recipient externalId", BroadcastRecipientStatus.FAILED);
        }

        if (contact.isSuppressed()) {
            return EligibilityResult.ineligible("Recipient has opted out or is suppressed", BroadcastRecipientStatus.SKIPPED_OPT_OUT);
        }

        // 1. TELEGRAM: Subscriber model; no 24-hour decay window
        if (channel == ChannelType.TELEGRAM) {
            return EligibilityResult.eligible();
        }

        // 2. WHATSAPP: Strict 24-hour customer care session window
        if (channel == ChannelType.WHATSAPP) {
            Optional<Conversation> convoOpt = conversationRepository.findByOrganizationIdAndContactIdAndChannel(organizationId, contact.getId(), channel);
            if (convoOpt.isEmpty()) {
                return skipExpiredWindow
                        ? EligibilityResult.ineligible("Outside WhatsApp 24-hour customer care session window (no conversation)", BroadcastRecipientStatus.SKIPPED_WINDOW)
                        : EligibilityResult.eligible();
            }

            var window = messagingWindowService.evaluateWindow(convoOpt.get());
            if (!window.isCanSendFreeform()) {
                return skipExpiredWindow
                        ? EligibilityResult.ineligible("WhatsApp 24-hour customer care session expired", BroadcastRecipientStatus.SKIPPED_WINDOW)
                        : EligibilityResult.eligible();
            }
            return EligibilityResult.eligible();
        }

        // 3. INSTAGRAM: Strict 24-hour window. Broadcast campaigns are automated marketing and cannot use 7-day human agent extension
        if (channel == ChannelType.INSTAGRAM) {
            Optional<Conversation> convoOpt = conversationRepository.findByOrganizationIdAndContactIdAndChannel(organizationId, contact.getId(), channel);
            if (convoOpt.isEmpty()) {
                return skipExpiredWindow
                        ? EligibilityResult.ineligible("Outside Instagram 24-hour customer care session window (no conversation)", BroadcastRecipientStatus.SKIPPED_WINDOW)
                        : EligibilityResult.eligible();
            }

            var window = messagingWindowService.evaluateWindow(convoOpt.get());
            if (!window.isCanSendFreeform()) {
                return skipExpiredWindow
                        ? EligibilityResult.ineligible("Instagram 24-hour session window expired (broadcasts cannot use 7-day human agent extension)", BroadcastRecipientStatus.SKIPPED_WINDOW)
                        : EligibilityResult.eligible();
            }
            return EligibilityResult.eligible();
        }

        return EligibilityResult.eligible();
    }

    @lombok.Value
    public static class EligibilityResult {
        boolean eligible;
        String reason;
        BroadcastRecipientStatus status;

        public static EligibilityResult eligible() {
            return new EligibilityResult(true, null, null);
        }

        public static EligibilityResult ineligible(String reason, BroadcastRecipientStatus status) {
            return new EligibilityResult(false, reason, status);
        }
    }

    private String personalize(String template, Contact contact, ChannelType channel) {
        String name = (contact.getFullName() != null && !contact.getFullName().isBlank())
                ? contact.getFullName()
                : (contact.getUsername() != null ? contact.getUsername() : "there");

        return template
                .replace("{{name}}", name)
                .replace("{{username}}", contact.getUsername() != null ? contact.getUsername() : "")
                .replace("{{channel}}", channel.name());
    }

    private String resolveChannelToken(UUID organizationId, ChannelType channel) {
        String token = "mock_channel_token";
        Optional<ConnectedAccount> accountOpt = connectedAccountRepository.findByOrganizationIdAndChannel(organizationId, channel);
        if (accountOpt.isPresent()) {
            try {
                token = tokenEncryptionService.decrypt(accountOpt.get().getEncryptedAccessToken());
            } catch (Exception e) {
                log.warn("Decryption failed for channel account, using fallback: {}", e.getMessage());
            }
        }
        return token;
    }

    private String dispatchMessage(ChannelType channel, String token, String recipientId, String text, String mediaUrl) {
        if (channel == ChannelType.INSTAGRAM) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                return instagramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                return instagramChannelProvider.sendPrivateDirectMessage(token, recipientId, text);
            }
        } else if (channel == ChannelType.WHATSAPP) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                return whatsAppChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                return whatsAppChannelProvider.sendMessage(token, recipientId, text);
            }
        } else if (channel == ChannelType.TELEGRAM) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                return telegramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                return telegramChannelProvider.sendMessage(token, recipientId, text);
            }
        }
        return null;
    }

    @Override
    @Transactional
    public int recoverStaleProcessingClaims(Duration leaseTimeout) {
        if (leaseTimeout == null || leaseTimeout.isNegative() || leaseTimeout.isZero()) {
            leaseTimeout = Duration.ofMinutes(5);
        }
        Instant staleBefore = Instant.now().minus(leaseTimeout);
        List<BroadcastRecipient> staleRecipients = campaignRecipientRepository.findStaleProcessingRecipients(staleBefore);
        if (staleRecipients.isEmpty()) {
            return 0;
        }

        log.warn("Found {} stale processing broadcast recipient claims (stale before {})", staleRecipients.size(), staleBefore);
        int recoveredCount = 0;

        for (BroadcastRecipient recipient : staleRecipients) {
            if (recipient.getProviderMessageId() != null && !recipient.getProviderMessageId().isBlank()) {
                // Provider confirmed acceptance prior to worker crash or lease expiry
                recipient.setStatus(BroadcastRecipientStatus.SENT);
                if (recipient.getSentAt() == null) {
                    recipient.setSentAt(recipient.getClaimedAt() != null ? recipient.getClaimedAt() : Instant.now());
                }
                recipient.setErrorMessage(null);
                log.info("Recovered stale recipient {} as SENT with confirmed providerMessageId: {}",
                        recipient.getId(), recipient.getProviderMessageId());
            } else {
                // Lease expired before provider acceptance was confirmed.
                // Blind re-dispatch is suppressed to avoid duplicate delivery across external messaging providers.
                recipient.setStatus(BroadcastRecipientStatus.FAILED_CRASH_RECOVERY);
                recipient.setErrorMessage("Dispatch lease expired without provider confirmation. Automatic retry suppressed to avoid duplicate delivery.");
                log.warn("Recovered stale recipient {} as FAILED_CRASH_RECOVERY without duplicate re-dispatch", recipient.getId());
            }
            campaignRecipientRepository.save(recipient);
            recoveredCount++;
        }

        return recoveredCount;
    }

    private CampaignResponse toCampaignResponse(BroadcastCampaign c) {
        return CampaignResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .channel(c.getChannel())
                .status(c.getStatus())
                .messageTemplate(c.getMessageTemplate())
                .mediaUrl(c.getMediaUrl())
                .targetTags(c.getTargetTags())
                .targetLeadStatus(c.getTargetLeadStatus())
                .minLeadScore(c.getMinLeadScore())
                .skipExpiredWindow(c.isSkipExpiredWindow())
                .scheduledAt(c.getScheduledAt())
                .startedAt(c.getStartedAt())
                .completedAt(c.getCompletedAt())
                .totalRecipients(c.getTotalRecipients())
                .sentCount(c.getSentCount())
                .deliveredCount(c.getDeliveredCount())
                .failedCount(c.getFailedCount())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private CampaignRecipientResponse toCampaignRecipientResponse(BroadcastRecipient r) {
        Contact c = r.getContact();
        return CampaignRecipientResponse.builder()
                .id(r.getId())
                .contactId(c != null ? c.getId() : null)
                .contactName(c != null ? c.getFullName() : null)
                .contactUsername(c != null ? c.getUsername() : null)
                .contactExternalId(c != null ? c.getExternalId() : null)
                .status(r.getStatus())
                .errorMessage(r.getErrorMessage())
                .sentAt(r.getSentAt())
                .idempotencyKey(r.getIdempotencyKey())
                .providerMessageId(r.getProviderMessageId())
                .attemptCount(r.getAttemptCount())
                .build();
    }
}
