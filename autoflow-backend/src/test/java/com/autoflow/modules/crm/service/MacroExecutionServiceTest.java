package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.dto.CrmDto.MessagingWindowResponse;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.ConversationSlaEventRepository;
import com.autoflow.modules.crm.repository.CrmMacroRepository;
import com.autoflow.modules.crm.service.impl.MacroExecutionServiceImpl;
import com.autoflow.modules.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MacroExecutionService Unit Tests")
class MacroExecutionServiceTest {

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
    private com.autoflow.modules.crm.repository.MessageRepository messageRepository;

    @Mock
    private CrmOutboxService outboxService;

    private MacroExecutionServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UUID orgId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();
    private final UUID macroId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MacroExecutionServiceImpl(
                crmMacroRepository,
                conversationRepository,
                crmService,
                messagingWindowService,
                conversationRoutingService,
                slaMonitoringService,
                cannedResponseService,
                slaEventRepository,
                userRepository,
                objectMapper,
                messageRepository
        );
    }

    @Test
    @DisplayName("Should execute composite macro with reply, tagging, priority, and resolution")
    void shouldExecuteCompositeMacro() {
        Contact contact = Contact.builder()
                .fullName("Jordan Lee")
                .username("jordan_l")
                .build();
        contact.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .priority(ConversationPriority.NORMAL)
                .build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Thank you for contacting us {{contact.name}}!"},
                    {"type": "ADD_TAGS", "tags": ["resolved_quick", "vip"]},
                    {"type": "SET_PRIORITY", "priority": "HIGH"},
                    {"type": "RESOLVE", "resolved": true}
                ]
                """;

        CrmMacro macro = CrmMacro.builder()
                .name("Quick Close VIP")
                .actionsJson(actionsJson)
                .build();
        macro.setId(macroId);
        macro.setOrganizationId(orgId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("ACTIVE_24H").build()
        );
        when(cannedResponseService.interpolateTemplate(eq(orgId), eq(convoId), anyString(), anyString()))
                .thenReturn("Thank you for contacting us Jordan Lee!");

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).hasSize(4);
        assertThat(result.isWindowExpiredSkipped()).isFalse();
        assertThat(result.isSuppressionSkipped()).isFalse();

        verify(crmService).sendAgentReply(orgId, convoId, "Thank you for contacting us Jordan Lee!", null, false);
        verify(crmService).addTagsToContact(eq(orgId), eq(contact.getId()), eq(List.of("resolved_quick", "vip")));
        verify(slaMonitoringService).applySlaPolicy(conversation);
        verify(crmService).resolveConversation(orgId, convoId, true);
    }

    @Test
    @DisplayName("Should skip outbound message when customer care window is expired but execute remaining actions")
    void shouldSkipOutboundWhenWindowExpired() {
        Contact contact = Contact.builder().fullName("Taylor Swift").build();
        contact.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Checking in!"},
                    {"type": "ADD_TAGS", "tags": ["needs_template"]}
                ]
                """;

        CrmMacro macro = CrmMacro.builder().name("Window Expired Tag").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));
        when(messagingWindowService.evaluateWindow(conversation)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("EXPIRED").build()
        );

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isWindowExpiredSkipped()).isTrue();
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
        verify(crmService).addTagsToContact(eq(orgId), eq(contact.getId()), eq(List.of("needs_template")));
    }

    @Test
    @DisplayName("Should skip outbound message when contact is suppressed/opted-out")
    void shouldSkipOutboundWhenContactSuppressed() {
        Contact contact = Contact.builder()
                .fullName("Opted Out User")
                .tags(List.of("opt_out"))
                .build();
        contact.setId(UUID.randomUUID());

        Conversation conversation = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.TELEGRAM)
                .build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Special promo!"}
                ]
                """;

        CrmMacro macro = CrmMacro.builder().name("Promo Send").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSuppressionSkipped()).isTrue();
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should reject macro creation when actionsJson contains invalid action type")
    void shouldRejectInvalidMacroActionType() {
        AgentProductivityDto.CrmMacroRequest req = AgentProductivityDto.CrmMacroRequest.builder()
                .name("Malicious Macro")
                .actionsJson("[{\"type\": \"EXECUTE_COMMAND\", \"cmd\": \"rm -rf\"}]")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createMacro(orgId, userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported macro action type");
    }

    @Test
    @DisplayName("Should reject macro creation when actionsJson contains unrecognized fields")
    void shouldRejectMacroWithUnrecognizedFields() {
        AgentProductivityDto.CrmMacroRequest req = AgentProductivityDto.CrmMacroRequest.builder()
                .name("Injection Macro")
                .actionsJson("[{\"type\": \"SEND_REPLY\", \"content\": \"Hello\", \"unexpectedField\": \"bad\"}]")
                .build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.createMacro(orgId, userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognized field 'unexpectedField'");
    }

    @Test
    @DisplayName("Should handle external provider dispatch failure in SEND_REPLY without failing other actions")
    void shouldHandleExternalDispatchFailureResiliently() {
        Contact contact = Contact.builder().fullName("Bob Martin").build();
        contact.setId(UUID.randomUUID());

        Conversation conv = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conv.setId(convoId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Welcome aboard!"},
                    {"type": "ADD_TAGS", "tags": ["welcome_sent"]}
                ]
                """;

        CrmMacro macro = CrmMacro.builder().name("Welcome Flow").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conv));
        when(messagingWindowService.evaluateWindow(conv)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("ACTIVE_24H").build()
        );
        when(cannedResponseService.interpolateTemplate(any(), any(), any(), any())).thenReturn("Welcome aboard!");

        // Simulate external network timeout during dispatch
        doThrow(new RuntimeException("Meta Cloud API connection timeout"))
                .when(crmService).sendAgentReply(any(), any(), any(), any(), anyBoolean());

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        // A timeout leaves the provider outcome unknown, so it is reported as ambiguous rather than
        // failed: the message may already have reached the customer and must not be retried blindly.
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_AMBIGUOUS"));
        assertThat(result.getActionsExecuted()).noneMatch(a -> a.contains("SEND_REPLY_FAILED"));
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("ADD_TAGS"));
        verify(crmService).addTagsToContact(eq(orgId), eq(contact.getId()), eq(List.of("welcome_sent")));
    }

    @Test
    @DisplayName("Should report a deterministic provider rejection as SEND_REPLY_FAILED without failing other actions")
    void shouldReportDeterministicDispatchRejectionAsFailed() {
        Contact contact = Contact.builder().fullName("Bob Martin").build();
        contact.setId(UUID.randomUUID());

        Conversation conv = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conv.setId(convoId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Welcome aboard!"},
                    {"type": "ADD_TAGS", "tags": ["welcome_sent"]}
                ]
                """;

        CrmMacro macro = CrmMacro.builder().name("Welcome Flow").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conv));
        when(messagingWindowService.evaluateWindow(conv)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("ACTIVE_24H").build()
        );
        when(cannedResponseService.interpolateTemplate(any(), any(), any(), any())).thenReturn("Welcome aboard!");

        // A provider rejection that is known to have not reached the customer
        doThrow(new RuntimeException("Meta Cloud API rejected the message: invalid template"))
                .when(crmService).sendAgentReply(any(), any(), any(), any(), anyBoolean());

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_FAILED"));
        assertThat(result.getActionsExecuted()).noneMatch(a -> a.contains("SEND_REPLY_AMBIGUOUS"));
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("ADD_TAGS"));
    }

    @Test
    @DisplayName("Should suppress duplicate SEND_REPLY dispatched within the 5s idempotency window")
    void shouldSuppressDuplicateSendReplyWithinWindow() {
        Contact contact = Contact.builder().fullName("Alice Smith").build();
        contact.setId(UUID.randomUUID());

        Conversation conv = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conv.setId(convoId);

        String actionsJson = """
                [
                    {"type": "SEND_REPLY", "content": "Already replied!"}
                ]
                """;

        CrmMacro macro = CrmMacro.builder().name("Duplicate Test").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conv));
        when(messagingWindowService.evaluateWindow(conv)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("ACTIVE_24H").build()
        );
        when(cannedResponseService.interpolateTemplate(any(), any(), any(), any())).thenReturn("Already replied!");

        // Mock recent outbound message already exists
        when(messageRepository.existsByConversationIdAndDirectionAndContentAndSentAtAfter(
                eq(convoId), eq("OUTBOUND"), eq("Already replied!"), any(Instant.class)
        )).thenReturn(true);

        ApplyMacroResult result = service.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_SKIPPED"));
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should suppress a duplicate SEND_REPLY when the transactional outbox already dispatched it")
    void shouldSuppressDuplicateDispatchViaOutbox() {
        MacroExecutionServiceImpl outboxServiceImpl = serviceWithOutbox();
        stubMacroWithSingleReply("Welcome aboard!");

        CrmOutboxMessage existing = CrmOutboxMessage.builder()
                .idempotencyKey("existing")
                .status(CrmOutboxService.STATUS_DISPATCHED)
                .attempts(1)
                .maxAttempts(3)
                .build();
        existing.setId(UUID.randomUUID());
        when(outboxService.register(eq(orgId), eq(convoId), eq(macroId), any(), any(), any(), any(), any()))
                .thenReturn(existing);

        ApplyMacroResult result = outboxServiceImpl.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_SKIPPED"));
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should skip a SEND_REPLY whose previous dispatch outcome was ambiguous")
    void shouldSkipAmbiguousOutboxEntry() {
        MacroExecutionServiceImpl outboxServiceImpl = serviceWithOutbox();
        stubMacroWithSingleReply("Welcome aboard!");

        CrmOutboxMessage existing = CrmOutboxMessage.builder()
                .idempotencyKey("existing")
                .status(CrmOutboxService.STATUS_AMBIGUOUS)
                .attempts(1)
                .maxAttempts(3)
                .build();
        existing.setId(UUID.randomUUID());
        when(outboxService.register(eq(orgId), eq(convoId), eq(macroId), any(), any(), any(), any(), any()))
                .thenReturn(existing);

        ApplyMacroResult result = outboxServiceImpl.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_SKIPPED"));
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should skip a SEND_REPLY whose dispatch attempt budget is exhausted")
    void shouldSkipExhaustedOutboxEntry() {
        MacroExecutionServiceImpl outboxServiceImpl = serviceWithOutbox();
        stubMacroWithSingleReply("Welcome aboard!");

        CrmOutboxMessage existing = CrmOutboxMessage.builder()
                .idempotencyKey("existing")
                .status(CrmOutboxService.STATUS_FAILED)
                .attempts(3)
                .maxAttempts(3)
                .build();
        existing.setId(UUID.randomUUID());
        when(outboxService.register(eq(orgId), eq(convoId), eq(macroId), any(), any(), any(), any(), any()))
                .thenReturn(existing);

        ApplyMacroResult result = outboxServiceImpl.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_SKIPPED"));
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Should register, attempt and mark the outbox entry when a SEND_REPLY is dispatched")
    void shouldRecordDispatchInOutbox() {
        MacroExecutionServiceImpl outboxServiceImpl = serviceWithOutbox();
        stubMacroWithSingleReply("Welcome aboard!");

        CrmOutboxMessage registered = CrmOutboxMessage.builder()
                .idempotencyKey("fresh")
                .status(CrmOutboxService.STATUS_PENDING)
                .attempts(0)
                .maxAttempts(3)
                .build();
        registered.setId(UUID.randomUUID());
        Message sent = Message.builder().deliveryStatus("DELIVERED").build();
        sent.setId(UUID.randomUUID());
        sent.setExternalMessageId("ext-999");

        when(outboxService.register(eq(orgId), eq(convoId), eq(macroId), any(), any(), any(), any(), any()))
                .thenReturn(registered);
        when(outboxService.claimForDispatch(registered.getId(), registered.getAttempts())).thenReturn(true);
        when(crmService.sendAgentReply(eq(orgId), eq(convoId), any(), any(), eq(false))).thenReturn(sent);

        ApplyMacroResult result = outboxServiceImpl.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY"));
        verify(outboxService).claimForDispatch(registered.getId(), registered.getAttempts());
        verify(outboxService).markDispatched(registered.getId(), sent.getId(), "ext-999");
    }

    @Test
    @DisplayName("Should not send a duplicate reply when a concurrent execution already claimed the dispatch")
    void shouldSkipDispatchWhenClaimLost() {
        MacroExecutionServiceImpl outboxServiceImpl = serviceWithOutbox();
        stubMacroWithSingleReply("Welcome aboard!");

        CrmOutboxMessage registered = CrmOutboxMessage.builder()
                .idempotencyKey("contended")
                .status(CrmOutboxService.STATUS_PENDING)
                .attempts(0)
                .maxAttempts(3)
                .build();
        registered.setId(UUID.randomUUID());

        when(outboxService.register(eq(orgId), eq(convoId), eq(macroId), any(), any(), any(), any(), any()))
                .thenReturn(registered);
        when(outboxService.claimForDispatch(registered.getId(), registered.getAttempts())).thenReturn(false);

        ApplyMacroResult result = outboxServiceImpl.applyMacro(orgId, convoId, macroId, userId, "Sarah Agent");

        assertThat(result.getActionsExecuted()).anyMatch(a -> a.contains("SEND_REPLY_SKIPPED"));
        verify(crmService, never()).sendAgentReply(any(), any(), any(), any(), eq(false));
        verify(outboxService, never()).markDispatched(any(), any(), any());
    }

    private MacroExecutionServiceImpl serviceWithOutbox() {
        return new MacroExecutionServiceImpl(
                crmMacroRepository,
                conversationRepository,
                crmService,
                messagingWindowService,
                conversationRoutingService,
                slaMonitoringService,
                cannedResponseService,
                slaEventRepository,
                userRepository,
                objectMapper,
                messageRepository,
                outboxService
        );
    }

    private void stubMacroWithSingleReply(String replyContent) {
        Contact contact = Contact.builder().fullName("Bob Martin").build();
        contact.setId(UUID.randomUUID());

        Conversation conv = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conv.setId(convoId);

        String actionsJson = "[{\"type\": \"SEND_REPLY\", \"content\": \"" + replyContent + "\"}]";

        CrmMacro macro = CrmMacro.builder().name("Welcome Flow").actionsJson(actionsJson).build();
        macro.setId(macroId);

        when(crmMacroRepository.findByIdAndOrganizationId(macroId, orgId)).thenReturn(Optional.of(macro));
        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conv));
        when(messagingWindowService.evaluateWindow(conv)).thenReturn(
                MessagingWindowResponse.builder().windowStatus("ACTIVE_24H").build()
        );
        when(cannedResponseService.interpolateTemplate(any(), any(), any(), any())).thenReturn(replyContent);
    }
}
