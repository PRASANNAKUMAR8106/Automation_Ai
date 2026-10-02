package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.AgentChannelSpecializationRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.ConversationSlaEventRepository;
import com.autoflow.modules.tenant.entity.Membership;
import com.autoflow.modules.tenant.repository.MembershipRepository;
import com.autoflow.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConversationRoutingServiceImpl implements ConversationRoutingService {

    private final ConversationRepository conversationRepository;
    private final MembershipRepository membershipRepository;
    private final AgentChannelSpecializationRepository specializationRepository;
    private final ConversationSlaEventRepository slaEventRepository;

    private final Map<UUID, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public ConversationRoutingServiceImpl(
            ConversationRepository conversationRepository,
            MembershipRepository membershipRepository,
            AgentChannelSpecializationRepository specializationRepository,
            ConversationSlaEventRepository slaEventRepository) {
        this.conversationRepository = conversationRepository;
        this.membershipRepository = membershipRepository;
        this.specializationRepository = specializationRepository;
        this.slaEventRepository = slaEventRepository;
    }

    public ConversationRoutingServiceImpl(
            ConversationRepository conversationRepository,
            MembershipRepository membershipRepository) {
        this(conversationRepository, membershipRepository, null, null);
    }

    @Override
    @Transactional
    public User assignConversation(Conversation conversation, RoutingPolicy routingPolicy) {
        if (routingPolicy == null || routingPolicy == RoutingPolicy.MANUAL) {
            return conversation.getAssignedUser();
        }

        UUID orgId = conversation.getOrganizationId();
        List<Membership> memberships = membershipRepository.findByOrganizationId(orgId);

        List<User> eligibleUsers = memberships.stream()
                .map(Membership::getUser)
                .filter(u -> u != null && u.isActive())
                .collect(Collectors.toList());

        if (eligibleUsers.isEmpty()) {
            log.info("No active users found for org [{}] to auto-assign conversation [{}]", orgId, conversation.getId());
            return conversation.getAssignedUser();
        }

        User previousUser = conversation.getAssignedUser();
        User selectedUser = null;

        if (routingPolicy == RoutingPolicy.ROUND_ROBIN) {
            AtomicInteger counter = roundRobinCounters.computeIfAbsent(orgId, k -> new AtomicInteger(0));
            int index = Math.abs(counter.getAndIncrement()) % eligibleUsers.size();
            selectedUser = eligibleUsers.get(index);
        } else if (routingPolicy == RoutingPolicy.LEAST_BUSY) {
            selectedUser = eligibleUsers.stream()
                    .min(Comparator.comparingLong(u ->
                            conversationRepository.countByOrganizationIdAndAssignedUserIdAndResolvedFalse(orgId, u.getId())))
                    .orElse(eligibleUsers.get(0));
        } else if (routingPolicy == RoutingPolicy.CHANNEL_SPECIALIST) {
            List<User> specialists = List.of();
            if (specializationRepository != null && conversation.getChannel() != null) {
                List<AgentChannelSpecialization> specs = specializationRepository
                        .findAllByOrganizationIdAndChannelAndActiveTrue(orgId, conversation.getChannel());
                specialists = specs.stream()
                        .map(AgentChannelSpecialization::getUser)
                        .filter(u -> u != null && u.isActive())
                        .collect(Collectors.toList());
            }

            if (!specialists.isEmpty()) {
                selectedUser = specialists.stream()
                        .min(Comparator.comparingLong(u ->
                                conversationRepository.countByOrganizationIdAndAssignedUserIdAndResolvedFalse(orgId, u.getId())))
                        .orElse(specialists.get(0));
                log.info("Selected channel specialist [{}] for channel [{}] in org [{}]",
                        selectedUser.getEmail(), conversation.getChannel(), orgId);
            } else {
                log.warn("No active channel specialist found for channel [{}] in org [{}], falling back to LEAST_BUSY",
                        conversation.getChannel(), orgId);
                selectedUser = eligibleUsers.stream()
                        .min(Comparator.comparingLong(u ->
                                conversationRepository.countByOrganizationIdAndAssignedUserIdAndResolvedFalse(orgId, u.getId())))
                        .orElse(eligibleUsers.get(0));
            }
        }

        if (selectedUser != null) {
            conversation.setAssignedUser(selectedUser);
            conversationRepository.save(conversation);
            log.info("Assigned conversation [{}] to user [{}] ({}) via [{}] policy",
                    conversation.getId(), selectedUser.getId(), selectedUser.getEmail(), routingPolicy);

            if (slaEventRepository != null) {
                ConversationSlaEventType eventType = previousUser == null
                        ? ConversationSlaEventType.ASSIGNMENT
                        : ConversationSlaEventType.REASSIGNMENT;
                ConversationSlaEvent event = ConversationSlaEvent.builder()
                        .conversation(conversation)
                        .slaPolicy(conversation.getSlaPolicy())
                        .eventType(eventType)
                        .assignedUser(selectedUser)
                        .previousUser(previousUser)
                        .details("Assigned via " + routingPolicy.name())
                        .build();
                event.setOrganizationId(orgId);
                slaEventRepository.save(event);
            }
        }

        return selectedUser;
    }

    @Override
    @Transactional
    public User assignConversation(UUID organizationId, UUID conversationId, RoutingPolicy routingPolicy) {
        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        return assignConversation(conversation, routingPolicy);
    }
}
