package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.tenant.entity.Membership;
import com.autoflow.modules.tenant.repository.MembershipRepository;
import com.autoflow.modules.user.entity.User;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ConversationRoutingServiceImpl implements ConversationRoutingService {

    private final ConversationRepository conversationRepository;
    private final MembershipRepository membershipRepository;

    private final Map<UUID, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

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
        }

        if (selectedUser != null) {
            conversation.setAssignedUser(selectedUser);
            conversationRepository.save(conversation);
            log.info("Assigned conversation [{}] to user [{}] ({}) via [{}] policy",
                    conversation.getId(), selectedUser.getId(), selectedUser.getEmail(), routingPolicy);
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
