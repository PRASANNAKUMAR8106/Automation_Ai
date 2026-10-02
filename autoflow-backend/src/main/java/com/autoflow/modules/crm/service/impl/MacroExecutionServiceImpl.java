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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
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

    @Override
    @Transactional
    public CrmMacro createMacro(UUID organizationId, UUID userId, CrmMacroRequest request) {
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
                            crmService.sendAgentReply(organizationId, conversationId, interpolated, null, false);
                            outboundSnippet = interpolated;
                            actionsExecuted.add("SEND_REPLY: " + (interpolated.length() > 30 ? interpolated.substring(0, 30) + "..." : interpolated));
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
                                    slaEventRepository.save(event);

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
