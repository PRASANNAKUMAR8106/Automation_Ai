package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.ApplyMacroResult;
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
                objectMapper
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
}
