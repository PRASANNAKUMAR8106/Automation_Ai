package com.autoflow.modules.crm.service.impl;

import com.autoflow.modules.crm.dto.AgentProductivityDto.AgentPresenceDto;
import com.autoflow.modules.crm.service.AgentCollisionService;
import com.autoflow.modules.crm.service.LiveChatStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentCollisionServiceImpl implements AgentCollisionService {

    private static final Duration LEASE_TTL = Duration.ofSeconds(30);

    private final LiveChatStreamService liveChatStreamService;

    // conversationId -> Map of userId -> PresenceRecord
    private final Map<UUID, Map<UUID, PresenceRecord>> presenceRegistry = new ConcurrentHashMap<>();

    private record PresenceRecord(UUID userId, String userEmail, String action, Instant lastActiveAt) {}

    @Override
    public List<AgentPresenceDto> recordPresence(UUID organizationId, UUID conversationId, UUID userId, String userEmail, String action) {
        Map<UUID, PresenceRecord> convMap = presenceRegistry.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>());

        if ("LEFT".equalsIgnoreCase(action)) {
            convMap.remove(userId);
        } else {
            String normalizedAction = "TYPING".equalsIgnoreCase(action) ? "TYPING" : "VIEWING";
            convMap.put(userId, new PresenceRecord(userId, userEmail, normalizedAction, Instant.now()));
        }

        List<AgentPresenceDto> activeList = cleanAndGetActive(conversationId, convMap);

        // Broadcast to all active clients of this conversation
        try {
            liveChatStreamService.broadcastAgentPresence(organizationId, conversationId, activeList);
        } catch (Exception e) {
            log.warn("Failed broadcasting agent presence for convo {}: {}", conversationId, e.getMessage());
        }

        return activeList;
    }

    @Override
    public List<AgentPresenceDto> releasePresence(UUID organizationId, UUID conversationId, UUID userId) {
        Map<UUID, PresenceRecord> convMap = presenceRegistry.get(conversationId);
        if (convMap != null) {
            convMap.remove(userId);
            List<AgentPresenceDto> activeList = cleanAndGetActive(conversationId, convMap);
            try {
                liveChatStreamService.broadcastAgentPresence(organizationId, conversationId, activeList);
            } catch (Exception e) {
                log.warn("Failed broadcasting agent presence release for convo {}: {}", conversationId, e.getMessage());
            }
            return activeList;
        }
        return Collections.emptyList();
    }

    @Override
    public List<AgentPresenceDto> getActiveViewers(UUID organizationId, UUID conversationId) {
        Map<UUID, PresenceRecord> convMap = presenceRegistry.get(conversationId);
        if (convMap == null || convMap.isEmpty()) {
            return Collections.emptyList();
        }
        return cleanAndGetActive(conversationId, convMap);
    }

    private List<AgentPresenceDto> cleanAndGetActive(UUID conversationId, Map<UUID, PresenceRecord> map) {
        Instant cutoff = Instant.now().minus(LEASE_TTL);
        map.entrySet().removeIf(entry -> entry.getValue().lastActiveAt().isBefore(cutoff));

        if (map.isEmpty()) {
            presenceRegistry.remove(conversationId);
            return Collections.emptyList();
        }

        return map.values().stream()
                .map(r -> AgentPresenceDto.builder()
                        .userId(r.userId())
                        .userEmail(r.userEmail())
                        .action(r.action())
                        .lastActiveAt(r.lastActiveAt())
                        .build())
                .sorted(Comparator.comparing(AgentPresenceDto::getLastActiveAt).reversed())
                .toList();
    }
}
