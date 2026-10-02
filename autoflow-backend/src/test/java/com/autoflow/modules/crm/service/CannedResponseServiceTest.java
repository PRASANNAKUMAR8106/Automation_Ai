package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.CannedResponse;
import com.autoflow.modules.crm.repository.CannedResponseRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.impl.CannedResponseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CannedResponseService Unit Tests")
class CannedResponseServiceTest {

    @Mock
    private CannedResponseRepository cannedResponseRepository;

    @Mock
    private ConversationRepository conversationRepository;

    private CannedResponseServiceImpl service;

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID convoId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CannedResponseServiceImpl(cannedResponseRepository, conversationRepository);
    }

    @Test
    @DisplayName("Should create canned response and normalize shortcut prefix")
    void shouldCreateCannedResponseAndNormalizeShortcut() {
        CannedResponseRequest req = CannedResponseRequest.builder()
                .shortcut("refund")
                .title("Standard Refund Policy")
                .content("We issue refunds within 14 days.")
                .category("BILLING")
                .build();

        when(cannedResponseRepository.findByOrganizationIdAndShortcut(orgId, "#refund")).thenReturn(Optional.empty());
        when(cannedResponseRepository.save(any(CannedResponse.class))).thenAnswer(i -> {
            CannedResponse saved = i.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CannedResponse result = service.createCannedResponse(orgId, userId, req);

        assertThat(result.getShortcut()).isEqualTo("#refund");
        assertThat(result.getTitle()).isEqualTo("Standard Refund Policy");
        assertThat(result.getCategory()).isEqualTo("BILLING");
        verify(cannedResponseRepository).save(any(CannedResponse.class));
    }

    @Test
    @DisplayName("Should reject duplicate shortcut in the same organization")
    void shouldRejectDuplicateShortcut() {
        CannedResponseRequest req = CannedResponseRequest.builder()
                .shortcut("#hours")
                .title("Office Hours")
                .content("We are open 9am to 6pm.")
                .build();

        when(cannedResponseRepository.findByOrganizationIdAndShortcut(orgId, "#hours"))
                .thenReturn(Optional.of(CannedResponse.builder().shortcut("#hours").build()));

        assertThatThrownBy(() -> service.createCannedResponse(orgId, userId, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should update existing canned response")
    void shouldUpdateCannedResponse() {
        UUID snippetId = UUID.randomUUID();
        CannedResponse existing = CannedResponse.builder()
                .shortcut("#pricing")
                .title("Pricing")
                .content("Starter is $29/mo")
                .category("SALES")
                .build();
        existing.setId(snippetId);
        existing.setOrganizationId(orgId);

        when(cannedResponseRepository.findByIdAndOrganizationId(snippetId, orgId)).thenReturn(Optional.of(existing));
        when(cannedResponseRepository.findByOrganizationIdAndShortcut(orgId, "#pricing")).thenReturn(Optional.of(existing));
        when(cannedResponseRepository.save(any(CannedResponse.class))).thenAnswer(i -> i.getArgument(0));

        CannedResponseRequest updateReq = CannedResponseRequest.builder()
                .shortcut("#pricing")
                .title("Updated Pricing")
                .content("Starter is $39/mo")
                .category("SALES")
                .build();

        CannedResponse updated = service.updateCannedResponse(orgId, snippetId, updateReq);

        assertThat(updated.getTitle()).isEqualTo("Updated Pricing");
        assertThat(updated.getContent()).isEqualTo("Starter is $39/mo");
    }

    @Test
    @DisplayName("Should interpolate template placeholders with contact and agent attributes")
    void shouldInterpolateTemplatePlaceholders() {
        Contact contact = Contact.builder()
                .fullName("Alex Morgan")
                .username("alex_m")
                .email("alex@example.com")
                .phone("+15551234567")
                .build();

        Conversation conversation = Conversation.builder()
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .build();
        conversation.setId(convoId);
        conversation.setOrganizationId(orgId);

        when(conversationRepository.findByIdAndOrganizationId(convoId, orgId)).thenReturn(Optional.of(conversation));

        String raw = "Hi {{contact.name}} ({{contact.username}}), your email is {{contact.email}} and channel is {{channel}}. Best, {{agent.name}} from {{organization.name}}.";
        String interpolated = service.interpolateTemplate(orgId, convoId, raw, "Sarah Agent");

        assertThat(interpolated).isEqualTo("Hi Alex Morgan (alex_m), your email is alex@example.com and channel is WHATSAPP. Best, Sarah Agent from AutoFlow.");
    }

    @Test
    @DisplayName("Should increment usage count")
    void shouldTrackUsage() {
        UUID snippetId = UUID.randomUUID();
        service.trackUsage(orgId, snippetId);
        verify(cannedResponseRepository).incrementUsageCount(snippetId, orgId);
    }

    @Test
    @DisplayName("Should enforce tenant boundary when deleting")
    void shouldEnforceTenantBoundaryOnDelete() {
        UUID snippetId = UUID.randomUUID();
        when(cannedResponseRepository.findByIdAndOrganizationId(snippetId, orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteCannedResponse(orgId, snippetId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
