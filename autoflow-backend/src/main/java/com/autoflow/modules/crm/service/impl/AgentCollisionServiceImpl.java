package com.autoflow.modules.crm.service.impl;

import com.autoflow.modules.crm.dto.AgentProductivityDto.AgentPresenceDto;
import com.autoflow.modules.crm.service.AgentCollisionService;
import com.autoflow.modules.crm.service.LiveChatStreamService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class AgentCollisionServiceImpl implements AgentCollisionService {

    private static final Duration LEASE_TTL = Duration.ofSeconds(30);

    private final LiveChatStreamService liveChatStreamService;
    private final ObjectMapper objectMapper;

    /** Null when Redis is not configured – service degrades to in-memory only. */
    @Nullable
    private final StringRedisTemplate stringRedisTemplate;

    // Fallback in-memory registry: conversationId -> Map of userId -> PresenceRecord
    private final Map<UUID, Map<UUID, PresenceRecord>> presenceRegistry = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicBoolean degraded = new java.util.concurrent.atomic.AtomicBoolean(false);

    private record PresenceRecord(UUID userId, String userEmail, String action, Instant lastActiveAt) {}

    public AgentCollisionServiceImpl(LiveChatStreamService liveChatStreamService) {
        this(liveChatStreamService, new ObjectMapper(), null);
    }

    public AgentCollisionServiceImpl(LiveChatStreamService liveChatStreamService, ObjectMapper objectMapper) {
        this(liveChatStreamService, objectMapper, null);
    }

    @Autowired
    public AgentCollisionServiceImpl(
            LiveChatStreamService liveChatStreamService,
            ObjectMapper objectMapper,
            @Autowired(required = false) @Nullable StringRedisTemplate stringRedisTemplate
    ) {
        this.liveChatStreamService = liveChatStreamService;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        if (stringRedisTemplate != null) {
            log.info("AgentCollisionService initialized with Redis-backed shared presence");
            this.degraded.set(false);
        } else {
            log.info("AgentCollisionService initialized with in-memory presence (no Redis)");
            this.degraded.set(true);
        }
    }

    @Override
    public boolean isDegradedMode() {
        return degraded.get();
    }

    private String getRedisKey(UUID organizationId, UUID conversationId) {
        return "crm:presence:org:" + organizationId + ":conv:" + conversationId;
    }

    @Override
    public List<AgentPresenceDto> recordPresence(UUID organizationId, UUID conversationId, UUID userId, String userEmail, String action) {
        String normalizedAction = "TYPING".equalsIgnoreCase(action) ? "TYPING" : "VIEWING";
        boolean isLeaving = "LEFT".equalsIgnoreCase(action);
        PresenceRecord record = new PresenceRecord(userId, userEmail, normalizedAction, Instant.now());

        // 1. In-memory local update
        Map<UUID, PresenceRecord> convMap = presenceRegistry.computeIfAbsent(conversationId, k -> new ConcurrentHashMap<>());
        if (isLeaving) {
            convMap.remove(userId);
        } else {
            convMap.put(userId, record);
        }

        // 2. Redis-backed shared cluster update (if configured)
        if (stringRedisTemplate != null) {
            try {
                String key = getRedisKey(organizationId, conversationId);
                if (isLeaving) {
                    stringRedisTemplate.opsForHash().delete(key, userId.toString());
                } else {
                    Map<String, Object> payload = Map.of(
                            "userId", userId.toString(),
                            "userEmail", userEmail != null ? userEmail : "",
                            "action", normalizedAction,
                            "lastActiveAt", record.lastActiveAt().toEpochMilli()
                    );
                    stringRedisTemplate.opsForHash().put(key, userId.toString(), objectMapper.writeValueAsString(payload));
                    stringRedisTemplate.expire(key, Duration.ofSeconds(35));
                }
                if (degraded.compareAndSet(true, false)) {
                    log.info("Redis presence write succeeded; cluster-authoritative mode active");
                }
            } catch (Exception e) {
                if (degraded.compareAndSet(false, true)) {
                    log.warn("Redis presence write failed, degraded to instance in-memory lease: {}", e.getMessage());
                } else {
                    log.debug("Redis presence update failed, relying on in-memory lease: {}", e.getMessage());
                }
            }
        }

        List<AgentPresenceDto> activeList = getActiveViewers(organizationId, conversationId);

        // 3. Broadcast to all active clients of this conversation
        try {
            liveChatStreamService.broadcastAgentPresence(organizationId, conversationId, activeList);
        } catch (Exception e) {
            log.warn("Failed broadcasting agent presence for convo {}: {}", conversationId, e.getMessage());
        }

        return activeList;
    }

    @Override
    public List<AgentPresenceDto> releasePresence(UUID organizationId, UUID conversationId, UUID userId) {
        // 1. In-memory local release
        Map<UUID, PresenceRecord> convMap = presenceRegistry.get(conversationId);
        if (convMap != null) {
            convMap.remove(userId);
        }

        // 2. Redis-backed shared release
        if (stringRedisTemplate != null) {
            try {
                String key = getRedisKey(organizationId, conversationId);
                stringRedisTemplate.opsForHash().delete(key, userId.toString());
            } catch (Exception e) {
                log.debug("Redis presence release failed: {}", e.getMessage());
            }
        }

        List<AgentPresenceDto> activeList = getActiveViewers(organizationId, conversationId);
        try {
            liveChatStreamService.broadcastAgentPresence(organizationId, conversationId, activeList);
        } catch (Exception e) {
            log.warn("Failed broadcasting agent presence release for convo {}: {}", conversationId, e.getMessage());
        }
        return activeList;
    }

    @Override
    public List<AgentPresenceDto> getActiveViewers(UUID organizationId, UUID conversationId) {
        Instant cutoff = Instant.now().minus(LEASE_TTL);

        // Try Redis cluster state first
        if (stringRedisTemplate != null) {
            try {
                String key = getRedisKey(organizationId, conversationId);
                Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
                List<AgentPresenceDto> redisList = new ArrayList<>();
                if (entries != null && !entries.isEmpty()) {
                    for (Map.Entry<Object, Object> entry : entries.entrySet()) {
                        try {
                            JsonNode node = objectMapper.readTree(entry.getValue().toString());
                            long epochMilli = node.path("lastActiveAt").asLong(0);
                            Instant lastActive = Instant.ofEpochMilli(epochMilli);
                            if (lastActive.isAfter(cutoff)) {
                                redisList.add(AgentPresenceDto.builder()
                                        .userId(UUID.fromString(node.path("userId").asText()))
                                        .userEmail(node.path("userEmail").asText(""))
                                        .action(node.path("action").asText("VIEWING"))
                                        .lastActiveAt(lastActive)
                                        .build());
                            }
                        } catch (Exception parseEx) {
                            log.debug("Error parsing Redis presence entry: {}", parseEx.getMessage());
                        }
                    }
                    redisList.sort(Comparator.comparing(AgentPresenceDto::getLastActiveAt).reversed());
                }
                if (degraded.compareAndSet(true, false)) {
                    log.info("Redis presence connection recovered; switching back to cluster-authoritative mode");
                }
                // Evict local in-memory presence for this conversation to prevent ghost presence upon Redis recovery
                presenceRegistry.remove(conversationId);
                // Redis call succeeded: return cluster authoritative list
                return redisList;
            } catch (Exception e) {
                if (degraded.compareAndSet(false, true)) {
                    log.warn("Redis presence degraded mode activated; falling back to instance in-memory lease: {}", e.getMessage());
                }
            }
        }

        // Fallback to local in-memory presence
        Map<UUID, PresenceRecord> convMap = presenceRegistry.get(conversationId);
        if (convMap == null || convMap.isEmpty()) {
            return Collections.emptyList();
        }

        convMap.entrySet().removeIf(entry -> entry.getValue().lastActiveAt().isBefore(cutoff));
        if (convMap.isEmpty()) {
            presenceRegistry.remove(conversationId);
            return Collections.emptyList();
        }

        return convMap.values().stream()
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
