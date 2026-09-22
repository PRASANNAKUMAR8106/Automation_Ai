package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrmServiceImpl implements CrmService {

    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public Contact getOrCreateContact(UUID organizationId, ChannelType channel, String externalId, String username, String fullName) {
        return contactRepository.findByOrganizationIdAndChannelAndExternalId(organizationId, channel, externalId)
                .map(existing -> {
                    if (username != null && !username.isBlank()) existing.setUsername(username);
                    if (fullName != null && !fullName.isBlank()) existing.setFullName(fullName);
                    existing.setLastInteractionAt(Instant.now());
                    return contactRepository.save(existing);
                })
                .orElseGet(() -> {
                    Contact newContact = Contact.builder()
                            .channel(channel)
                            .externalId(externalId)
                            .username(username)
                            .fullName(fullName)
                            .leadStatus(LeadStatus.NEW)
                            .lastInteractionAt(Instant.now())
                            .build();
                    newContact.setOrganizationId(organizationId);
                    log.info("Created new CRM contact {} ({}) for org {}", externalId, username, organizationId);
                    return contactRepository.save(newContact);
                });
    }

    @Override
    @Transactional
    public Conversation getOrCreateConversation(UUID organizationId, Contact contact) {
        return conversationRepository.findByOrganizationIdAndContactIdAndChannel(organizationId, contact.getId(), contact.getChannel())
                .orElseGet(() -> {
                    Conversation convo = Conversation.builder()
                            .organizationId(organizationId)
                            .contact(contact)
                            .channel(contact.getChannel())
                            .lastMessageAt(Instant.now())
                            .build();
                    return conversationRepository.save(convo);
                });
    }

    @Override
    @Transactional
    public Message recordMessage(UUID organizationId, Conversation conversation, String direction, String senderType, String messageType, String content, String mediaUrl, String externalMessageId) {
        Message msg = Message.builder()
                .organizationId(organizationId)
                .conversation(conversation)
                .direction(direction)
                .senderType(senderType)
                .messageType(messageType != null ? messageType : "TEXT")
                .content(content)
                .mediaUrl(mediaUrl)
                .externalMessageId(externalMessageId)
                .sentAt(Instant.now())
                .deliveryStatus("DELIVERED")
                .build();

        msg = messageRepository.save(msg);
        conversation.setLastMessageAt(Instant.now());
        conversationRepository.save(conversation);
        return msg;
    }

    @Override
    @Transactional
    public void addTagsToContact(UUID organizationId, UUID contactId, List<String> tags) {
        if (tags == null || tags.isEmpty()) return;

        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        if (!contact.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }

        Set<String> tagSet = new HashSet<>(contact.getTags() != null ? contact.getTags() : List.of());
        tagSet.addAll(tags);
        contact.setTags(new ArrayList<>(tagSet));
        contactRepository.save(contact);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contact> getContacts(UUID organizationId) {
        return contactRepository.findByOrganizationId(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> getConversations(UUID organizationId) {
        return conversationRepository.findByOrganizationIdOrderByLastMessageAtDesc(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID organizationId, UUID conversationId) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
    }
}
