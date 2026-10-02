package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.AgentPresenceDto;
import com.autoflow.modules.crm.service.impl.AgentCollisionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AgentCollisionService Unit Tests")
class AgentCollisionServiceTest {

    @Mock
    private LiveChatStreamService liveChatStreamService;

    private AgentCollisionServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();
    private final UUID agent1 = UUID.randomUUID();
    private final UUID agent2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AgentCollisionServiceImpl(liveChatStreamService);
    }

    @Test
    @DisplayName("Should record agent presence and return active list")
    void shouldRecordPresenceAndBroadcast() {
        List<AgentPresenceDto> active = service.recordPresence(orgId, convoId, agent1, "alice@autoflow.ai", "VIEWING");

        assertThat(active).hasSize(1);
        assertThat(active.get(0).getUserId()).isEqualTo(agent1);
        assertThat(active.get(0).getUserEmail()).isEqualTo("alice@autoflow.ai");
        assertThat(active.get(0).getAction()).isEqualTo("VIEWING");

        verify(liveChatStreamService).broadcastAgentPresence(eq(orgId), eq(convoId), eq(active));
    }

    @Test
    @DisplayName("Should detect collision when multiple agents are active on same conversation")
    void shouldTrackMultipleActiveAgents() {
        service.recordPresence(orgId, convoId, agent1, "alice@autoflow.ai", "VIEWING");
        List<AgentPresenceDto> active = service.recordPresence(orgId, convoId, agent2, "bob@autoflow.ai", "TYPING");

        assertThat(active).hasSize(2);
        assertThat(active).extracting(AgentPresenceDto::getUserEmail)
                .containsExactlyInAnyOrder("alice@autoflow.ai", "bob@autoflow.ai");
    }

    @Test
    @DisplayName("Should release presence when agent navigates away")
    void shouldReleasePresenceExplicitly() {
        service.recordPresence(orgId, convoId, agent1, "alice@autoflow.ai", "VIEWING");
        List<AgentPresenceDto> remaining = service.releasePresence(orgId, convoId, agent1);

        assertThat(remaining).isEmpty();
        verify(liveChatStreamService).broadcastAgentPresence(eq(orgId), eq(convoId), eq(List.of()));
    }

    @Test
    @DisplayName("Should return active viewers without re-registering")
    void shouldGetActiveViewers() {
        service.recordPresence(orgId, convoId, agent1, "alice@autoflow.ai", "VIEWING");
        List<AgentPresenceDto> viewers = service.getActiveViewers(orgId, convoId);

        assertThat(viewers).hasSize(1);
        assertThat(viewers.get(0).getUserEmail()).isEqualTo("alice@autoflow.ai");
    }
}
