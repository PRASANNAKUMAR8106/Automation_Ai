package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.CsatSurveyRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CsatServiceImpl implements CsatService {

    private final ConversationRepository conversationRepository;
    private final ContactRepository contactRepository;
    private final CsatSurveyRepository csatSurveyRepository;
    private final MessagingWindowService messagingWindowService;
    private final MessageRepository messageRepository;

    public static final String CSAT_PROMPT_TEXT =
            "How would you rate your support experience today? Please reply with a rating from 1 (Poor) to 5 (Excellent).";

    @Override
    @Transactional
    public CsatSurveyResponse triggerPostResolutionSurvey(UUID organizationId, UUID conversationId) {
        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        Contact contact = conversation.getContact();

        // Check if survey already exists
        Optional<CsatSurvey> existing = csatSurveyRepository.findByOrganizationIdAndConversationId(organizationId, conversationId);
        if (existing.isPresent()) {
            return toSurveyResponse(existing.get());
        }

        // 1. Re-check persistent consent and suppression (Phase 19 compliance)
        Contact freshContact = contactRepository.findById(contact.getId()).orElse(contact);
        if (freshContact.isSuppressed() || freshContact.isOptedOut()) {
            log.info("CSAT survey skipped for conversation [{}]: Recipient [{}] is opted out or suppressed.",
                    conversationId, freshContact.getId());
            CsatSurvey skipped = CsatSurvey.builder()
                    .conversation(conversation)
                    .contact(freshContact)
                    .assignedUser(conversation.getAssignedUser())
                    .status(CsatStatus.SKIPPED_SUPPRESSED)
                    .build();
            skipped.setOrganizationId(organizationId);
            return toSurveyResponse(csatSurveyRepository.save(skipped));
        }

        // 2. Channel session window compliance (Phase 18 compliance)
        CrmDto.MessagingWindowResponse window = messagingWindowService.evaluateWindow(conversation);
        if (!window.isCanSendFreeform()) {
            log.info("CSAT survey skipped for conversation [{}]: 24-hour customer care session window expired.",
                    conversationId);
            CsatSurvey skipped = CsatSurvey.builder()
                    .conversation(conversation)
                    .contact(freshContact)
                    .assignedUser(conversation.getAssignedUser())
                    .status(CsatStatus.SKIPPED_WINDOW_EXPIRED)
                    .build();
            skipped.setOrganizationId(organizationId);
            return toSurveyResponse(csatSurveyRepository.save(skipped));
        }

        // 3. Dispatch CSAT Survey
        Message surveyMessage = Message.builder()
                .organizationId(organizationId)
                .conversation(conversation)
                .direction("OUTBOUND")
                .senderType("BOT")
                .messageType("TEXT")
                .content(CSAT_PROMPT_TEXT)
                .sentAt(Instant.now())
                .deliveryStatus("DELIVERED")
                .build();
        messageRepository.save(surveyMessage);

        CsatSurvey survey = CsatSurvey.builder()
                .conversation(conversation)
                .contact(freshContact)
                .assignedUser(conversation.getAssignedUser())
                .status(CsatStatus.DISPATCHED)
                .dispatchedAt(Instant.now())
                .build();
        survey.setOrganizationId(organizationId);

        CsatSurvey saved = csatSurveyRepository.save(survey);
        log.info("Dispatched CSAT survey [{}] for conversation [{}] in org [{}]",
                saved.getId(), conversationId, organizationId);

        return toSurveyResponse(saved);
    }

    @Override
    @Transactional
    public CsatSurveyResponse submitFeedback(UUID organizationId, UUID conversationId, SubmitCsatRequest request) {
        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        CsatSurvey survey = csatSurveyRepository.findByOrganizationIdAndConversationId(organizationId, conversationId)
                .orElseGet(() -> {
                    CsatSurvey newSurvey = CsatSurvey.builder()
                            .conversation(conversation)
                            .contact(conversation.getContact())
                            .assignedUser(conversation.getAssignedUser())
                            .status(CsatStatus.DISPATCHED)
                            .dispatchedAt(Instant.now())
                            .build();
                    newSurvey.setOrganizationId(organizationId);
                    return newSurvey;
                });

        survey.setRating(request.getRating());
        survey.setFeedbackText(request.getFeedbackText());
        survey.setRespondedAt(Instant.now());
        survey.setStatus(CsatStatus.COMPLETED);

        // If low rating (<= 2), flag conversation for supervisor attention
        if (request.getRating() <= 2) {
            conversation.setPriority(ConversationPriority.HIGH);
            conversationRepository.save(conversation);
            log.warn("Low CSAT rating ({}/5) received for conversation [{}]. Priority raised to HIGH for supervisor review.",
                    request.getRating(), conversationId);
        }

        CsatSurvey saved = csatSurveyRepository.save(survey);
        return toSurveyResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CsatSurveyResponse getSurvey(UUID organizationId, UUID conversationId) {
        return csatSurveyRepository.findByOrganizationIdAndConversationId(organizationId, conversationId)
                .map(this::toSurveyResponse)
                .orElse(null);
    }

    private CsatSurveyResponse toSurveyResponse(CsatSurvey s) {
        return CsatSurveyResponse.builder()
                .id(s.getId())
                .conversationId(s.getConversation() != null ? s.getConversation().getId() : null)
                .contactId(s.getContact() != null ? s.getContact().getId() : null)
                .rating(s.getRating())
                .feedbackText(s.getFeedbackText())
                .status(s.getStatus().name())
                .dispatchedAt(s.getDispatchedAt())
                .respondedAt(s.getRespondedAt())
                .build();
    }
}
