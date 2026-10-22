package com.autoflow.modules.crm.service.impl;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.dto.CrmDto.MessagingWindowResponse;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.ConversationSlaEventRepository;
import com.autoflow.modules.crm.repository.CrmMacroRepository;
import com.autoflow.modules.crm.service.*;
import com.autoflow.modules.user.entity.User;
import com.autoflow.modules.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoflow.modules.crm.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Service
public class MacroExecutionServiceImpl implements MacroExecutionService {

    private final CrmMacroRepository crmMacroRepository;
    private final ConversationRepository conversationRepository;
    private final CrmService crmService;
    private final MessagingWindowService messagingWindowService;
    private final ConversationRoutingService conversationRoutingService;
    private final SlaMonitoringService slaMonitoringService;
    private final CannedResponseService cannedResponseService;
    private final ConversationSlaEventRepository slaEventRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    @Nullable
    private final MessageRepository messageRepository;
    @Nullable
    private final CrmOutboxService outboxService;

    public MacroExecutionServiceImpl(
            CrmMacroRepository crmMacroRepository,
            ConversationRepository conversationRepository,
            CrmService crmService,
            MessagingWindowService messagingWindowService,
            ConversationRoutingService conversationRoutingService,
            SlaMonitoringService slaMonitoringService,
            CannedResponseService cannedResponseService,
            ConversationSlaEventRepository slaEventRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this(crmMacroRepository, conversationRepository, crmService, messagingWindowService,
                conversationRoutingService, slaMonitoringService, cannedResponseService,
                slaEventRepository, userRepository, objectMapper, null, null);
    }

    public MacroExecutionServiceImpl(
            CrmMacroRepository crmMacroRepository,
            ConversationRepository conversationRepository,
            CrmService crmService,
            MessagingWindowService messagingWindowService,
            ConversationRoutingService conversationRoutingService,
            SlaMonitoringService slaMonitoringService,
            CannedResponseService cannedResponseService,
            ConversationSlaEventRepository slaEventRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Nullable MessageRepository messageRepository
    ) {
        this(crmMacroRepository, conversationRepository, crmService, messagingWindowService,
                conversationRoutingService, slaMonitoringService, cannedResponseService,
                slaEventRepository, userRepository, objectMapper, messageRepository, null);
    }

    @Autowired
    public MacroExecutionServiceImpl(
            CrmMacroRepository crmMacroRepository,
            ConversationRepository conversationRepository,
            CrmService crmService,
            MessagingWindowService messagingWindowService,
            ConversationRoutingService conversationRoutingService,
            SlaMonitoringService slaMonitoringService,
            CannedResponseService cannedResponseService,
            ConversationSlaEventRepository slaEventRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            @Autowired(required = false) @Nullable MessageRepository messageRepository,
            @Autowired(required = false) @Nullable CrmOutboxService outboxService
    ) {
        this.crmMacroRepository = crmMacroRepository;
        this.conversationRepository = conversationRepository;
        this.crmService = crmService;
        this.messagingWindowService = messagingWindowService;
        this.conversationRoutingService = conversationRoutingService;
        this.slaMonitoringService = slaMonitoringService;
        this.cannedResponseService = cannedResponseService;
        this.slaEventRepository = slaEventRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.messageRepository = messageRepository;
        this.outboxService = outboxService;
    }

    @Override
    @Transactional
    public CrmMacro createMacro(UUID organizationId, UUID userId, CrmMacroRequest request) {
        MacroActionValidator.validate(request.getActionsJson(), objectMapper);

        CrmMacro macro = CrmMacro.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .actionsJson(request.getActionsJson().trim())
                .createdByUserId(userId)
                .build();
        macro.setOrganizationId(organizationId);
        return crmMacroRepository.save(macro);
    }

    @Override
    @Transactional
    public CrmMacro updateMacro(UUID organizationId, UUID id, CrmMacroRequest request) {
        MacroActionValidator.validate(request.getActionsJson(), objectMapper);

        CrmMacro macro = crmMacroRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CrmMacro", id));

        macro.setName(request.getName().trim());
        macro.setDescription(request.getDescription());
        macro.setActionsJson(request.getActionsJson().trim());

        return crmMacroRepository.save(macro);
    }

    @Override
    @Transactional
    public void deleteMacro(UUID organizationId, UUID id) {
        CrmMacro macro = crmMacroRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CrmMacro", id));
        crmMacroRepository.delete(macro);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CrmMacro> getMacros(UUID organizationId) {
        return crmMacroRepository.findByOrganizationIdOrderByNameAsc(organizationId);
    }

    @Override
    @Transactional
    public ApplyMacroResult applyMacro(UUID organizationId, UUID conversationId, UUID macroId, UUID executingUserId, String agentName) {
        CrmMacro macro = crmMacroRepository.findByIdAndOrganizationId(macroId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CrmMacro", macroId));

        MacroActionValidator.validate(macro.getActionsJson(), objectMapper);

        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        List<String> actionsExecuted = new ArrayList<>();
        String outboundSnippet = null;
        boolean windowExpiredSkipped = false;
        boolean suppressionSkipped = false;

        try {
            JsonNode root = objectMapper.readTree(macro.getActionsJson());
            List<JsonNode> actionNodes = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(actionNodes::add);
            } else if (root.isObject()) {
                actionNodes.add(root);
            }

            for (JsonNode node : actionNodes) {
                String type = node.path("type").asText("").trim().toUpperCase();

                switch (type) {
                    case "SEND_REPLY" -> {
                        String rawContent = node.path("content").asText("");
                        if (!rawContent.isBlank()) {
                            Contact contact = conversation.getContact();
                            if (contact != null && contact.isSuppressed()) {
                                suppressionSkipped = true;
                                log.info("Macro [{}] SEND_REPLY skipped: contact is suppressed", macro.getName());
                                break;
                            }

                            MessagingWindowResponse window = messagingWindowService.evaluateWindow(conversation);
                            if ("EXPIRED".equalsIgnoreCase(window.getWindowStatus())) {
                                windowExpiredSkipped = true;
                                log.info("Macro [{}] SEND_REPLY skipped: customer care window expired", macro.getName());
                                break;
                            }

                            String interpolated = cannedResponseService.interpolateTemplate(organizationId, conversationId, rawContent, agentName);

                            // 1. Transactional Outbox Check & Pre-persistence.
                            // The outbox entry is committed in its own transaction *before* the provider is
                            // contacted, so an abandoned dispatch always leaves a durable trace behind.
                            String idempotencyKey = "macro:" + macro.getId() + ":conv:" + conversationId + ":hash:" +
                                    AuditChecksumUtils.computeCanonicalChecksum(organizationId, conversationId, macro.getId(), "MACRO_REPLY", "SEND_REPLY", agentName, interpolated, null);

                            CrmOutboxMessage outbox = null;
                            if (outboxService != null) {
                                try {
                                    outbox = outboxService.register(
                                            organizationId,
                                            conversationId,
                                            macro.getId(),
                                            conversation.getChannel() != null ? conversation.getChannel().name() : "WHATSAPP",
                                            contact != null ? contact.getExternalId() : "",
                                            interpolated,
                                            null,
                                            idempotencyKey);
                                } catch (org.springframework.dao.DataIntegrityViolationException concurrentRegistration) {
                                    // A concurrent execution registered the same dispatch first and owns it.
                                    log.info("Macro [{}] SEND_REPLY skipped: a concurrent execution already registered this dispatch", macro.getName());
                                    actionsExecuted.add("SEND_REPLY_SKIPPED: Concurrent dispatch already registered for this message");
                                    break;
                                }

                                String outboxStatus = outbox.getStatus();
                                if (CrmOutboxService.STATUS_DISPATCHED.equalsIgnoreCase(outboxStatus)) {
                                    log.info("Macro [{}] SEND_REPLY skipped: duplicate outbox entry already dispatched", macro.getName());
                                    actionsExecuted.add("SEND_REPLY_SKIPPED: Duplicate dispatch suppressed via transactional outbox");
                                    break;
                                } else if (CrmOutboxService.STATUS_AMBIGUOUS.equalsIgnoreCase(outboxStatus)) {
                                    log.warn("Macro [{}] SEND_REPLY skipped: previous dispatch status ambiguous; reconciliation required", macro.getName());
                                    actionsExecuted.add("SEND_REPLY_SKIPPED: Previous dispatch ambiguous; manual reconciliation required");
                                    break;
                                } else if (outbox.getAttempts() >= outbox.getMaxAttempts()) {
                                    log.warn("Macro [{}] SEND_REPLY skipped: dispatch attempt budget exhausted", macro.getName());
                                    actionsExecuted.add("SEND_REPLY_SKIPPED: Maximum dispatch attempts exhausted");
                                    break;
                                }

                                // Claim the dispatch atomically. If a concurrent execution claimed it
                                // first, this one must not send the message to the customer a second time.
                                if (!outboxService.claimForDispatch(outbox.getId(), outbox.getAttempts())) {
                                    log.info("Macro [{}] SEND_REPLY skipped: dispatch claimed by a concurrent execution", macro.getName());
                                    actionsExecuted.add("SEND_REPLY_SKIPPED: Dispatch claimed by a concurrent execution");
                                    break;
                                }
                            }

                            // 2. In-memory / repository 5-second idempotency window fallback if outbox not configured
                            if (outbox == null && messageRepository != null && messageRepository.existsByConversationIdAndDirectionAndContentAndSentAtAfter(
                                    conversationId, "OUTBOUND", interpolated, java.time.Instant.now().minusSeconds(5))) {
                                log.info("Macro [{}] SEND_REPLY skipped: duplicate dispatch detected within 5s window", macro.getName());
                                actionsExecuted.add("SEND_REPLY_SKIPPED: Duplicate dispatch suppressed within idempotency window");
                                break;
                            }

                            // 3. Dispatch to CRM live chat / provider
                            try {
                                Message sentMsg = crmService.sendAgentReply(organizationId, conversationId, interpolated, null, false);
                                if (sentMsg != null && "FAILED".equalsIgnoreCase(sentMsg.getDeliveryStatus())) {
                                    log.warn("Macro [{}] SEND_REPLY provider delivery failed", macro.getName());
                                    if (outbox != null) {
                                        outboxService.markFailed(outbox.getId(), "External provider delivery failed");
                                    }
                                    actionsExecuted.add("SEND_REPLY_FAILED: External provider delivery failed");
                                } else if (sentMsg != null && "AMBIGUOUS".equalsIgnoreCase(sentMsg.getDeliveryStatus())) {
                                    log.warn("Macro [{}] SEND_REPLY provider delivery ambiguous", macro.getName());
                                    if (outbox != null) {
                                        outboxService.markAmbiguous(outbox.getId(), "Provider response unconfirmed (ambiguous network outcome)");
                                    }
                                    actionsExecuted.add("SEND_REPLY_AMBIGUOUS: Dispatch unconfirmed; provider response ambiguous");
                                } else {
                                    if (outbox != null) {
                                        outboxService.markDispatched(outbox.getId(),
                                                sentMsg != null ? sentMsg.getId() : null,
                                                sentMsg != null ? sentMsg.getExternalMessageId() : null);
                                    }
                                    outboundSnippet = interpolated;
                                    actionsExecuted.add("SEND_REPLY: " + (interpolated.length() > 30 ? interpolated.substring(0, 30) + "..." : interpolated));
                                }
                            } catch (Exception dispatchEx) {
                                boolean isAmbiguous = dispatchEx instanceof java.net.SocketTimeoutException
                                        || (dispatchEx.getMessage() != null && dispatchEx.getMessage().toLowerCase().contains("timeout"));
                                if (outbox != null) {
                                    if (isAmbiguous) {
                                        outboxService.markAmbiguous(outbox.getId(), dispatchEx.getMessage());
                                    } else {
                                        outboxService.markFailed(outbox.getId(), dispatchEx.getMessage());
                                    }
                                }
                                if (isAmbiguous) {
                                    log.warn("Macro [{}] SEND_REPLY external dispatch outcome ambiguous: {}", macro.getName(), dispatchEx.getMessage());
                                    actionsExecuted.add("SEND_REPLY_AMBIGUOUS: " + dispatchEx.getMessage());
                                } else {
                                    log.warn("Macro [{}] SEND_REPLY external dispatch failed: {}", macro.getName(), dispatchEx.getMessage());
                                    actionsExecuted.add("SEND_REPLY_FAILED: " + dispatchEx.getMessage());
                                }
                            }
                        }
                    }
                    case "ADD_TAGS" -> {
                        List<String> tags = new ArrayList<>();
                        if (node.path("tags").isArray()) {
                            node.path("tags").forEach(t -> tags.add(t.asText()));
                        } else if (node.path("tags").isTextual()) {
                            tags.addAll(Arrays.asList(node.path("tags").asText().split(",")));
                        }
                        if (!tags.isEmpty() && conversation.getContact() != null) {
                            crmService.addTagsToContact(organizationId, conversation.getContact().getId(), tags);
                            actionsExecuted.add("ADD_TAGS: " + tags);
                        }
                    }
                    case "REMOVE_TAGS" -> {
                        List<String> tags = new ArrayList<>();
                        if (node.path("tags").isArray()) {
                            node.path("tags").forEach(t -> tags.add(t.asText()));
                        } else if (node.path("tags").isTextual()) {
                            tags.addAll(Arrays.asList(node.path("tags").asText().split(",")));
                        }
                        if (conversation.getContact() != null) {
                            for (String tag : tags) {
                                crmService.removeTagFromContact(organizationId, conversation.getContact().getId(), tag);
                            }
                            actionsExecuted.add("REMOVE_TAGS: " + tags);
                        }
                    }
                    case "SET_PRIORITY" -> {
                        String p = node.path("priority").asText("NORMAL").trim().toUpperCase();
                        try {
                            ConversationPriority priority = ConversationPriority.valueOf(p);
                            conversation.setPriority(priority);
                            slaMonitoringService.applySlaPolicy(conversation);
                            conversationRepository.save(conversation);
                            actionsExecuted.add("SET_PRIORITY: " + priority.name());
                        } catch (IllegalArgumentException e) {
                            log.warn("Invalid priority [{}] in macro [{}]", p, macro.getName());
                        }
                    }
                    case "ASSIGN_AGENT" -> {
                        boolean channelSpecialist = node.path("channelSpecialist").asBoolean(false);
                        if (channelSpecialist) {
                            conversationRoutingService.assignConversation(conversation, RoutingPolicy.CHANNEL_SPECIALIST);
                            actionsExecuted.add("ASSIGN_AGENT: CHANNEL_SPECIALIST");
                        } else if (node.hasNonNull("userId")) {
                            try {
                                UUID targetUserId = UUID.fromString(node.path("userId").asText());
                                Optional<User> targetUser = userRepository.findById(targetUserId);
                                if (targetUser.isPresent()) {
                                    User previousUser = conversation.getAssignedUser();
                                    conversation.setAssignedUser(targetUser.get());
                                    conversationRepository.save(conversation);

                                    ConversationSlaEvent event = ConversationSlaEvent.builder()
                                            .conversation(conversation)
                                            .eventType(ConversationSlaEventType.REASSIGNMENT)
                                            .previousUser(previousUser)
                                            .assignedUser(targetUser.get())
                                            .reason("Macro applied: " + macro.getName())
                                            .details("Manually assigned to user " + targetUser.get().getEmail())
                                            .build();
                                    event.setOrganizationId(organizationId);
                                    event = slaEventRepository.save(event);
                                    AuditChecksumUtils.sealSlaEvent(organizationId, conversation.getId(), event);

                                    actionsExecuted.add("ASSIGN_AGENT: " + targetUser.get().getEmail());
                                }
                            } catch (Exception e) {
                                log.warn("Failed assigning agent in macro: {}", e.getMessage());
                            }
                        }
                    }
                    case "RESOLVE" -> {
                        boolean resolved = node.path("resolved").asBoolean(true);
                        crmService.resolveConversation(organizationId, conversationId, resolved);
                        actionsExecuted.add("RESOLVE: " + resolved);
                    }
                    default -> log.debug("Unknown action type [{}] in macro [{}]", type, macro.getName());
                }
            }

        } catch (Exception e) {
            log.error("Failed executing macro [{}] on conversation [{}]: {}", macroId, conversationId, e.getMessage(), e);
            return ApplyMacroResult.builder()
                    .macroId(macroId)
                    .macroName(macro.getName())
                    .success(false)
                    .actionsExecuted(actionsExecuted)
                    .outboundMessageSnippet(outboundSnippet)
                    .windowExpiredSkipped(windowExpiredSkipped)
                    .suppressionSkipped(suppressionSkipped)
                    .build();
        }

        return ApplyMacroResult.builder()
                .macroId(macroId)
                .macroName(macro.getName())
                .success(true)
                .actionsExecuted(actionsExecuted)
                .outboundMessageSnippet(outboundSnippet)
                .windowExpiredSkipped(windowExpiredSkipped)
                .suppressionSkipped(suppressionSkipped)
                .build();
    }
}
