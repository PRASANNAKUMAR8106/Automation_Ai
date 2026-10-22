package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.ConversationInternalNote;
import com.autoflow.modules.crm.entity.LeadStatus;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationInternalNoteRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import com.autoflow.modules.tenant.entity.Organization;
import com.autoflow.modules.tenant.repository.OrganizationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the audit ledger against a real database instead of mocks.
 *
 * <p>The mock based unit tests can only assert that a checksum was computed in memory. These tests
 * prove that the checksum actually reaches the {@code audit_checksum} column - which is the whole
 * point of the tamper evident ledger, since a checksum that is never persisted cannot detect
 * tampering at all.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Audit Checksum Persistence Integration Tests")
class AuditChecksumPersistenceIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private ConversationInternalNoteRepository internalNoteRepository;
    @Autowired
    private CrmService crmService;

    @Test
    @Transactional
    @DisplayName("Should persist a verifiable audit checksum for an outbound message")
    void shouldPersistAuditChecksumForMessage() {
        Conversation conversation = seedConversation();

        Message message = crmService.recordMessage(
                conversation.getOrganizationId(), conversation, "OUTBOUND", "AGENT", "TEXT",
                "Hello, this is a checksummed reply", null, "ext-12345");

        assertThat(message.getAuditChecksum()).isNotBlank();

        // Re-read from the database: the checksum must have been written to the row, not just held in memory.
        Message persisted = messageRepository.findById(message.getId()).orElseThrow();
        assertThat(persisted.getAuditChecksum()).isEqualTo(message.getAuditChecksum());

        String expected = AuditChecksumUtils.computeMessageChecksum(
                conversation.getOrganizationId(), conversation.getId(), persisted);
        assertThat(AuditChecksumUtils.verifyChecksum(expected, persisted.getAuditChecksum())).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("Should persist a verifiable audit checksum for an internal note")
    void shouldPersistAuditChecksumForInternalNote() {
        Conversation conversation = seedConversation();

        ConversationInternalNote note = ConversationInternalNote.builder()
                .conversation(conversation)
                .authorUserId(UUID.randomUUID())
                .authorEmail("agent@autoflow.ai")
                .noteType("INTERNAL_NOTE")
                .content("Checking with the billing team about the refund.")
                .build();
        note.setOrganizationId(conversation.getOrganizationId());

        ConversationInternalNote saved = internalNoteRepository.save(note);
        AuditChecksumUtils.sealInternalNote(conversation.getOrganizationId(), conversation.getId(), saved);

        ConversationInternalNote persisted = internalNoteRepository.findById(saved.getId()).orElseThrow();
        assertThat(persisted.getAuditChecksum()).isNotBlank();
        assertThat(persisted.getAuditChecksum()).isEqualTo(saved.getAuditChecksum());

        String expected = AuditChecksumUtils.computeInternalNoteChecksum(
                conversation.getOrganizationId(), conversation.getId(), persisted);
        assertThat(AuditChecksumUtils.verifyChecksum(expected, persisted.getAuditChecksum())).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("Should detect a message whose protected content was modified in the database")
    void shouldDetectContentTamperingInDatabase() {
        Conversation conversation = seedConversation();

        Message message = crmService.recordMessage(
                conversation.getOrganizationId(), conversation, "INBOUND", "CONTACT", "TEXT",
                "Original confidential text", null, null);

        String originalChecksum = message.getAuditChecksum();
        assertThat(originalChecksum).isNotBlank();

        // Simulate an out-of-band modification of a protected column.
        Message managed = messageRepository.findById(message.getId()).orElseThrow();
        managed.setContent("Tampered content injected directly into the database");
        messageRepository.saveAndFlush(managed);

        Message reloaded = messageRepository.findById(message.getId()).orElseThrow();
        String recomputed = AuditChecksumUtils.computeMessageChecksum(
                conversation.getOrganizationId(), conversation.getId(), reloaded);
        assertThat(AuditChecksumUtils.verifyChecksum(recomputed, reloaded.getAuditChecksum())).isFalse();
    }

    private Conversation seedConversation() {
        Organization organization = organizationRepository.save(
                Organization.builder().name("Acme " + UUID.randomUUID()).slug("acme-" + UUID.randomUUID()).build());

        Contact contact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId("+1555" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000))
                .fullName("Test Customer")
                .leadStatus(LeadStatus.NEW)
                .build();
        contact.setOrganizationId(organization.getId());
        contact = contactRepository.save(contact);

        Conversation conversation = Conversation.builder()
                .organizationId(organization.getId())
                .contact(contact)
                .channel(ChannelType.WHATSAPP)
                .lastMessageAt(Instant.now())
                .build();
        return conversationRepository.save(conversation);
    }
}
