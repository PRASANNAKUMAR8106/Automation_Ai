package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.*;
import com.autoflow.modules.crm.service.impl.*;
import com.autoflow.modules.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 22 Agent Productivity Concurrency & Race Condition Tests")
class AgentProductivityConcurrencyTest {

    @Mock
    private LiveChatStreamService liveChatStreamService;

    @Mock
    private CrmMacroRepository crmMacroRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private CrmService crmService;

    @Mock
    private MessagingWindowService messagingWindowService;

    @Mock
    private ConversationRoutingService conversationRoutingService;

    @Mock
    private SlaMonitoringService slaMonitoringService;

    @Mock
    private CannedResponseService cannedResponseService;

    @Mock
    private ConversationSlaEventRepository slaEventRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConversationInternalNoteRepository internalNoteRepository;

    private AgentCollisionServiceImpl collisionService;
    private MacroExecutionServiceImpl macroService;
    private InternalNoteServiceImpl internalNoteService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID orgId = UUID.randomUUID();
    private final UUID convId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        collisionService = new AgentCollisionServiceImpl(liveChatStreamService, objectMapper, null);

        macroService = new MacroExecutionServiceImpl(
                crmMacroRepository,
                conversationRepository,
                crmService,
                messagingWindowService,
                conversationRoutingService,
                slaMonitoringService,
                cannedResponseService,
                slaEventRepository,
                userRepository,
                objectMapper
        );

        internalNoteService = new InternalNoteServiceImpl(
                internalNoteRepository,
                conversationRepository,
                liveChatStreamService
        );
    }

    @Test
    @DisplayName("Concurrent presence heartbeats from multiple agents must not throw ConcurrentModificationException and preserve state")
    void shouldHandleConcurrentPresenceHeartbeats() throws Exception {
        int numThreads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numThreads);

        List<UUID> agentIds = new ArrayList<>();
        for (int i = 0; i < numThreads; i++) {
            agentIds.add(UUID.randomUUID());
        }

        for (int i = 0; i < numThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    collisionService.recordPresence(
                            orgId,
                            convId,
                            agentIds.get(index),
                            "agent" + index + "@autoflow.ai",
                            (index % 2 == 0) ? "VIEWING" : "TYPING"
                    );
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();

        List<AgentPresenceDto> viewers = collisionService.getActiveViewers(orgId, convId);
        assertThat(viewers).hasSize(numThreads);
    }

    @Test
    @DisplayName("Concurrent presence registration and release from the same agent must resolve race cleanly")
    void shouldHandleConcurrentPresenceToggle() throws Exception {
        int iterations = 50;
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(iterations * 2);
        UUID agentId = UUID.randomUUID();

        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    collisionService.recordPresence(orgId, convId, agentId, "agent@autoflow.ai", "TYPING");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    collisionService.releasePresence(orgId, convId, agentId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        // State must be either 0 or 1 without crashing
        List<AgentPresenceDto> active = collisionService.getActiveViewers(orgId, convId);
        assertThat(active.size()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Concurrent internal note creation on the same conversation must save all notes atomically")
    void shouldHandleConcurrentNoteCreation() throws Exception {
        Conversation conversation = Conversation.builder().channel(ChannelType.WHATSAPP).build();
        conversation.setId(convId);
        when(conversationRepository.findByIdAndOrganizationId(convId, orgId)).thenReturn(Optional.of(conversation));
        when(internalNoteRepository.save(any(ConversationInternalNote.class))).thenAnswer(inv -> {
            ConversationInternalNote note = inv.getArgument(0);
            note.setId(UUID.randomUUID());
            return note;
        });

        int numNotes = 25;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numNotes);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < numNotes; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    PostInternalNoteRequest req = PostInternalNoteRequest.builder()
                            .content("Concurrent note #" + index)
                            .noteType(index % 3 == 0 ? "SUPERVISOR_WHISPER" : "INTERNAL_NOTE")
                            .build();

                    InternalNoteResponse res = internalNoteService.createInternalNote(
                            orgId, convId, UUID.randomUUID(), "agent" + index + "@autoflow.ai", req
                    );
                    if (res != null && res.getId() != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(numNotes);
        verify(internalNoteRepository, times(numNotes)).save(any(ConversationInternalNote.class));
    }

    @Test
    @DisplayName("Concurrent macro application on the same conversation executes all actions safely")
    void shouldHandleConcurrentMacroExecution() throws Exception {
        UUID macroId = UUID.randomUUID();
        String actionsJson = """
                [
                    {"type": "ADD_TAGS", "tags": ["VIP", "ESCALATED"]},
                    {"type": "SET_PRIORITY", "priority": "HIGH"}
                ]
                """;

        CrmMacro macro = CrmMacro.builder()
                .name("High Priority VIP")
                .actionsJson(actionsJson)
                .build();
        macro.setId(macroId);

        Conversation conv = Conversation.builder()
                .channel(ChannelType.WHATSAPP)
                .contact(Contact.builder().build())
                .build();
        conv.setId(convId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convId, orgId)).thenReturn(Optional.of(conv));

        int numExecutions = 15;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(numExecutions);
        AtomicInteger successfulMacros = new AtomicInteger(0);

        for (int i = 0; i < numExecutions; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ApplyMacroResult result = macroService.applyMacro(orgId, convId, macroId, UUID.randomUUID(), "Agent Alice");
                    if (result.isSuccess()) {
                        successfulMacros.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successfulMacros.get()).isEqualTo(numExecutions);
        verify(slaMonitoringService, times(numExecutions)).applySlaPolicy(any(Conversation.class));
    }
}
