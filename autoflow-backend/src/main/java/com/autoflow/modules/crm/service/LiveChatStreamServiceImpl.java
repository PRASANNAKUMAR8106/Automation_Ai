package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.CrmDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class LiveChatStreamServiceImpl implements LiveChatStreamService {

    private static final Long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    // conversationId -> list of active conversation subscribers
    private final Map<UUID, List<SseEmitter>> conversationEmitters = new ConcurrentHashMap<>();

    // organizationId -> list of active tenant inbox subscribers
    private final Map<UUID, List<SseEmitter>> tenantInboxEmitters = new ConcurrentHashMap<>();

    // emitter -> tenant ID for security tracking
    private final Map<SseEmitter, UUID> emitterTenantMap = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribeToConversation(UUID organizationId, UUID conversationId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitterTenantMap.put(emitter, organizationId);

        conversationEmitters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeConversationEmitter(conversationId, emitter));
        emitter.onTimeout(() -> removeConversationEmitter(conversationId, emitter));
        emitter.onError(e -> removeConversationEmitter(conversationId, emitter));

        // Send initial acknowledgment
        try {
            emitter.send(SseEmitter.event()
                    .name("connection_ack")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "conversationId", conversationId.toString(),
                            "tenantId", organizationId.toString()
                    )));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE connection ack for conversation {}: {}", conversationId, e.getMessage());
            removeConversationEmitter(conversationId, emitter);
        }

        log.debug("Registered SSE client for conversation [{}] under tenant [{}]", conversationId, organizationId);
        return emitter;
    }

    @Override
    public SseEmitter subscribeToTenantInbox(UUID organizationId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT_MS);
        emitterTenantMap.put(emitter, organizationId);

        tenantInboxEmitters.computeIfAbsent(organizationId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeTenantEmitter(organizationId, emitter));
        emitter.onTimeout(() -> removeTenantEmitter(organizationId, emitter));
        emitter.onError(e -> removeTenantEmitter(organizationId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("inbox_ack")
                    .data(Map.of(
                            "status", "CONNECTED",
                            "tenantId", organizationId.toString()
                    )));
        } catch (IOException e) {
            log.warn("Failed to send initial SSE inbox ack for tenant {}: {}", organizationId, e.getMessage());
            removeTenantEmitter(organizationId, emitter);
        }

        log.debug("Registered SSE client for tenant inbox [{}]", organizationId);
        return emitter;
    }

    @Override
    public void broadcastMessage(UUID organizationId, UUID conversationId, CrmDto.MessageResponse message) {
        // 1. Send to subscribers of this specific conversation
        List<SseEmitter> convSubscribers = conversationEmitters.get(conversationId);
        if (convSubscribers != null && !convSubscribers.isEmpty()) {
            for (SseEmitter emitter : convSubscribers) {
                // Multi-tenant check
                UUID emitterOrg = emitterTenantMap.get(emitter);
                if (organizationId.equals(emitterOrg)) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("new_message")
                                .id(message.getId().toString())
                                .data(message));
                    } catch (Exception e) {
                        log.debug("Failed sending message event to conversation emitter: {}", e.getMessage());
                        removeConversationEmitter(conversationId, emitter);
                    }
                }
            }
        }

        // 2. Send update to tenant inbox subscribers
        List<SseEmitter> tenantSubscribers = tenantInboxEmitters.get(organizationId);
        if (tenantSubscribers != null && !tenantSubscribers.isEmpty()) {
            Map<String, Object> inboxUpdate = Map.of(
                    "type", "NEW_MESSAGE",
                    "conversationId", conversationId.toString(),
                    "senderType", message.getSenderType(),
                    "snippet", message.getContent() != null ? message.getContent() : "[Media]",
                    "sentAt", message.getSentAt() != null ? message.getSentAt().toString() : ""
            );
            for (SseEmitter emitter : tenantSubscribers) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("inbox_update")
                            .data(inboxUpdate));
                } catch (Exception e) {
                    log.debug("Failed sending inbox update event: {}", e.getMessage());
                    removeTenantEmitter(organizationId, emitter);
                }
            }
        }
    }

    @Override
    public void broadcastTyping(UUID organizationId, UUID conversationId, boolean isTyping) {
        List<SseEmitter> subscribers = conversationEmitters.get(conversationId);
        if (subscribers != null && !subscribers.isEmpty()) {
            Map<String, Object> typingEvent = Map.of(
                    "conversationId", conversationId.toString(),
                    "isTyping", isTyping
            );
            for (SseEmitter emitter : subscribers) {
                UUID emitterOrg = emitterTenantMap.get(emitter);
                if (organizationId.equals(emitterOrg)) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("typing")
                                .data(typingEvent));
                    } catch (Exception e) {
                        removeConversationEmitter(conversationId, emitter);
                    }
                }
            }
        }
    }

    @Override
    public void broadcastConversationResolved(UUID organizationId, UUID conversationId, boolean isResolved) {
        Map<String, Object> resolveEvent = Map.of(
                "conversationId", conversationId.toString(),
                "isResolved", isResolved
        );

        // Notify conversation subscribers
        List<SseEmitter> convSubscribers = conversationEmitters.get(conversationId);
        if (convSubscribers != null) {
            for (SseEmitter emitter : convSubscribers) {
                if (organizationId.equals(emitterTenantMap.get(emitter))) {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("conversation_resolved")
                                .data(resolveEvent));
                    } catch (Exception e) {
                        removeConversationEmitter(conversationId, emitter);
                    }
                }
            }
        }

        // Notify inbox subscribers
        List<SseEmitter> tenantSubscribers = tenantInboxEmitters.get(organizationId);
        if (tenantSubscribers != null) {
            for (SseEmitter emitter : tenantSubscribers) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("inbox_update")
                            .data(resolveEvent));
                } catch (Exception e) {
                    removeTenantEmitter(organizationId, emitter);
                }
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeats() {
        // Send ping to conversation emitters
        conversationEmitters.forEach((convId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (Exception e) {
                    removeConversationEmitter(convId, emitter);
                }
            }
        });

        // Send ping to tenant inbox emitters
        tenantInboxEmitters.forEach((orgId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("keep-alive"));
                } catch (Exception e) {
                    removeTenantEmitter(orgId, emitter);
                }
            }
        });
    }

    private void removeConversationEmitter(UUID conversationId, SseEmitter emitter) {
        emitterTenantMap.remove(emitter);
        List<SseEmitter> emitters = conversationEmitters.get(conversationId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                conversationEmitters.remove(conversationId);
            }
        }
    }

    private void removeTenantEmitter(UUID organizationId, SseEmitter emitter) {
        emitterTenantMap.remove(emitter);
        List<SseEmitter> emitters = tenantInboxEmitters.get(organizationId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                tenantInboxEmitters.remove(organizationId);
            }
        }
    }
}
