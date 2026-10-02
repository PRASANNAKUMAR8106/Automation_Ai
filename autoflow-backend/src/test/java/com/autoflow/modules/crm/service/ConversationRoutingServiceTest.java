package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.RoutingPolicy;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.tenant.entity.Membership;
import com.autoflow.modules.tenant.repository.MembershipRepository;
import com.autoflow.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationRoutingService Unit Tests")
class ConversationRoutingServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MembershipRepository membershipRepository;

    private ConversationRoutingServiceImpl routingService;

    private final UUID testOrgId = UUID.randomUUID();

    private User userAlice;
    private User userBob;

    @BeforeEach
    void setUp() {
        routingService = new ConversationRoutingServiceImpl(conversationRepository, membershipRepository);

        userAlice = User.builder()
                .email("alice@autoflow.ai")
                .active(true)
                .build();
        userAlice.setId(UUID.randomUUID());

        userBob = User.builder()
                .email("bob@autoflow.ai")
                .active(true)
                .build();
        userBob.setId(UUID.randomUUID());
    }

    private Conversation createConversation() {
        Conversation c = Conversation.builder()
                .build();
        c.setId(UUID.randomUUID());
        c.setOrganizationId(testOrgId);
        return c;
    }

    @Test
    @DisplayName("Should rotate agent assignment cyclically when policy is ROUND_ROBIN")
    void shouldRouteRoundRobin() {
        Membership m1 = Membership.builder().user(userAlice).organizationId(testOrgId).build();
        Membership m2 = Membership.builder().user(userBob).organizationId(testOrgId).build();
        when(membershipRepository.findByOrganizationId(testOrgId)).thenReturn(List.of(m1, m2));

        Conversation convo1 = createConversation();
        User assigned1 = routingService.assignConversation(convo1, RoutingPolicy.ROUND_ROBIN);
        assertNotNull(assigned1);

        Conversation convo2 = createConversation();
        User assigned2 = routingService.assignConversation(convo2, RoutingPolicy.ROUND_ROBIN);
        assertNotNull(assigned2);

        assertNotEquals(assigned1.getId(), assigned2.getId(), "Consecutive assignments must alternate across users in round robin");
        verify(conversationRepository, times(2)).save(any(Conversation.class));
    }

    @Test
    @DisplayName("Should assign to least busy agent when policy is LEAST_BUSY")
    void shouldRouteLeastBusy() {
        Membership m1 = Membership.builder().user(userAlice).organizationId(testOrgId).build();
        Membership m2 = Membership.builder().user(userBob).organizationId(testOrgId).build();
        when(membershipRepository.findByOrganizationId(testOrgId)).thenReturn(List.of(m1, m2));

        // Alice has 5 active conversations, Bob has 1
        when(conversationRepository.countByOrganizationIdAndAssignedUserIdAndResolvedFalse(testOrgId, userAlice.getId())).thenReturn(5L);
        when(conversationRepository.countByOrganizationIdAndAssignedUserIdAndResolvedFalse(testOrgId, userBob.getId())).thenReturn(1L);

        Conversation convo = createConversation();
        User assigned = routingService.assignConversation(convo, RoutingPolicy.LEAST_BUSY);

        assertNotNull(assigned);
        assertEquals(userBob.getId(), assigned.getId(), "LEAST_BUSY must select Bob who has fewer active conversations");
        assertEquals(userBob, convo.getAssignedUser());
        verify(conversationRepository).save(convo);
    }

    @Test
    @DisplayName("Should leave conversation unassigned when policy is MANUAL or no active users exist")
    void shouldKeepUnassignedWhenManualOrEmpty() {
        Conversation convo = createConversation();
        User assigned = routingService.assignConversation(convo, RoutingPolicy.MANUAL);
        assertNull(assigned);
        verifyNoInteractions(membershipRepository);
    }
}
