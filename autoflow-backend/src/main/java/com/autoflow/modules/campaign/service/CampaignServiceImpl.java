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
            if (isContactEligibleForChannel(organizationId, c, request.getChannel())) {
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

        // Fetch matching recipients
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

        campaign.setStatus(BroadcastCampaignStatus.CANCELLED);
        campaign = campaignRepository.save(campaign);
        log.info("Cancelled campaign {} for org {}", campaignId, organizationId);
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
            try {
                executeCampaign(campaign.getId());
            } catch (Exception e) {
                log.error("Failed to trigger scheduled campaign {}: {}", campaign.getId(), e.getMessage(), e);
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
        if (campaign.getStatus() != BroadcastCampaignStatus.SCHEDULED && campaign.getStatus() != BroadcastCampaignStatus.RUNNING) {
            return;
        }

        campaign.setStatus(BroadcastCampaignStatus.RUNNING);
        campaign.setStartedAt(Instant.now());
        campaign = campaignRepository.save(campaign);

        log.info("Executing broadcast campaign {} ({}) on channel {}", campaign.getId(), campaign.getName(), campaign.getChannel());

        List<BroadcastRecipient> pendingRecipients = campaignRecipientRepository.findByCampaignIdAndStatus(
                campaign.getId(),
                BroadcastRecipientStatus.PENDING
        );

        String token = resolveChannelToken(campaign.getOrganizationId(), campaign.getChannel());

        int sent = 0;
        int failed = 0;

        for (BroadcastRecipient recipient : pendingRecipients) {
            Contact contact = recipient.getContact();

            // 1. Meta 24-hour compliance check
            if (campaign.isSkipExpiredWindow() && !isContactEligibleForChannel(campaign.getOrganizationId(), contact, campaign.getChannel())) {
                recipient.setStatus(BroadcastRecipientStatus.SKIPPED_WINDOW);
                recipient.setErrorMessage("Skipped: Outside Meta 24-hour customer care session window.");
                campaignRecipientRepository.save(recipient);
                continue;
            }

            // 2. Personalize template variables
            String personalizedMessage = personalize(campaign.getMessageTemplate(), contact, campaign.getChannel());

            // 3. Dispatch to Channel Provider
            try {
                dispatchMessage(campaign.getChannel(), token, contact.getExternalId(), personalizedMessage, campaign.getMediaUrl());
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

        campaign.setSentCount(campaign.getSentCount() + sent);
        campaign.setDeliveredCount(campaign.getDeliveredCount() + sent);
        campaign.setFailedCount(campaign.getFailedCount() + failed);
        campaign.setCompletedAt(Instant.now());
        campaign.setStatus(BroadcastCampaignStatus.COMPLETED);
        campaignRepository.save(campaign);

        log.info("Completed campaign {}: {} sent, {} failed, {} skipped window",
                campaign.getId(), sent, failed, campaign.getTotalRecipients() - (sent + failed));
    }

    private List<Contact> getMatchingContacts(UUID organizationId, ChannelType channel, List<String> tags, LeadStatus leadStatus, int minLeadScore) {
        List<Contact> all = contactRepository.findByOrganizationIdAndChannel(organizationId, channel);
        return all.stream()
                .filter(c -> {
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

    private boolean isContactEligibleForChannel(UUID organizationId, Contact contact, ChannelType channel) {
        if (channel == ChannelType.TELEGRAM) {
            return true;
        }
        Optional<Conversation> convoOpt = conversationRepository.findByOrganizationIdAndContactIdAndChannel(organizationId, contact.getId(), channel);
        if (convoOpt.isEmpty()) {
            return false;
        }
        return messagingWindowService.evaluateWindow(convoOpt.get()).isCanSendFreeform();
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

    private void dispatchMessage(ChannelType channel, String token, String recipientId, String text, String mediaUrl) {
        if (channel == ChannelType.INSTAGRAM) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                instagramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                instagramChannelProvider.sendPrivateDirectMessage(token, recipientId, text);
            }
        } else if (channel == ChannelType.WHATSAPP) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                whatsAppChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                whatsAppChannelProvider.sendMessage(token, recipientId, text);
            }
        } else if (channel == ChannelType.TELEGRAM) {
            if (mediaUrl != null && !mediaUrl.isBlank()) {
                telegramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
            } else {
                telegramChannelProvider.sendMessage(token, recipientId, text);
            }
        }
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
                .build();
    }
}
