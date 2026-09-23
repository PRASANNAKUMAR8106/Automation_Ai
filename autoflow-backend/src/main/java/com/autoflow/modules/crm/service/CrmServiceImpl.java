package com.autoflow.modules.crm.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.provider.instagram.InstagramChannelProvider;
import com.autoflow.modules.channel.provider.whatsapp.WhatsAppChannelProvider;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.dto.CrmDto;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrmServiceImpl implements CrmService {

    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConnectedAccountRepository connectedAccountRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final InstagramChannelProvider instagramChannelProvider;
    private final WhatsAppChannelProvider whatsAppChannelProvider;
    private final com.autoflow.modules.channel.provider.telegram.TelegramChannelProvider telegramChannelProvider;
    private final MessagingWindowService messagingWindowService;
    private final LiveChatStreamService liveChatStreamService;

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

        // Update conversation timestamps
        Instant now = Instant.now();
        conversation.setLastMessageAt(now);
        if ("INBOUND".equalsIgnoreCase(direction) && "CONTACT".equalsIgnoreCase(senderType)) {
            conversation.setLastCustomerMessageAt(now);
        }
        conversationRepository.save(conversation);

        // Broadcast real-time SSE event to conversation subscribers and tenant inbox
        try {
            CrmDto.MessageResponse msgResponse = CrmDto.MessageResponse.builder()
                    .id(msg.getId())
                    .conversationId(conversation.getId())
                    .direction(msg.getDirection())
                    .senderType(msg.getSenderType())
                    .messageType(msg.getMessageType())
                    .content(msg.getContent())
                    .mediaUrl(msg.getMediaUrl())
                    .deliveryStatus(msg.getDeliveryStatus())
                    .sentAt(msg.getSentAt())
                    .build();
            liveChatStreamService.broadcastMessage(organizationId, conversation.getId(), msgResponse);
        } catch (Exception e) {
            log.warn("Failed to broadcast live chat message event: {}", e.getMessage());
        }

        return msg;
    }

    @Override
    @Transactional
    public void addTagsToContact(UUID organizationId, UUID contactId, List<String> tags) {
        if (tags == null || tags.isEmpty()) return;

        Contact contact = getContactById(organizationId, contactId);
        Set<String> tagSet = new HashSet<>(contact.getTags() != null ? contact.getTags() : List.of());
        tagSet.addAll(tags);
        contact.setTags(new ArrayList<>(tagSet));
        contactRepository.save(contact);
    }

    @Override
    @Transactional
    public void removeTagFromContact(UUID organizationId, UUID contactId, String tag) {
        if (tag == null || tag.isBlank()) return;

        Contact contact = getContactById(organizationId, contactId);
        if (contact.getTags() != null && contact.getTags().contains(tag)) {
            List<String> updated = new ArrayList<>(contact.getTags());
            updated.remove(tag);
            contact.setTags(updated);
            contactRepository.save(contact);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Contact getContactById(UUID organizationId, UUID contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        if (!contact.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
        return contact;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contact> getContacts(UUID organizationId) {
        return contactRepository.findByOrganizationId(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contact> getContacts(UUID organizationId, String search, String tag) {
        List<Contact> all = contactRepository.findByOrganizationId(organizationId);
        return all.stream()
                .filter(c -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.toLowerCase();
                        boolean matchesUsername = c.getUsername() != null && c.getUsername().toLowerCase().contains(s);
                        boolean matchesFullName = c.getFullName() != null && c.getFullName().toLowerCase().contains(s);
                        boolean matchesEmail = c.getEmail() != null && c.getEmail().toLowerCase().contains(s);
                        if (!matchesUsername && !matchesFullName && !matchesEmail) return false;
                    }
                    if (tag != null && !tag.isBlank()) {
                        if (c.getTags() == null || !c.getTags().contains(tag)) return false;
                    }
                    return true;
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Conversation getConversationById(UUID organizationId, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));
        if (!conversation.getOrganizationId().equals(organizationId)) {
            throw new ResourceNotFoundException("Conversation", conversationId);
        }
        return conversation;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversation> getConversations(UUID organizationId) {
        return conversationRepository.findByOrganizationIdOrderByLastMessageAtDesc(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID organizationId, UUID conversationId) {
        getConversationById(organizationId, conversationId); // Assert existence and tenant boundary
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
    }

    @Override
    @Transactional
    public Message sendAgentReply(UUID organizationId, UUID conversationId, String content, String mediaUrl) {
        return sendAgentReply(organizationId, conversationId, content, mediaUrl, false);
    }

    @Override
    @Transactional
    public Message sendAgentReply(UUID organizationId, UUID conversationId, String content, String mediaUrl, boolean humanAgentTag) {
        Conversation conversation = getConversationById(organizationId, conversationId);

        // Validate Meta / WhatsApp 24-hour compliance
        messagingWindowService.validateCanSend(conversation, humanAgentTag);

        Contact contact = conversation.getContact();
        ChannelType channel = conversation.getChannel();
        String recipientId = contact.getExternalId();

        // Resolve access token from connected account
        String token = "mock_channel_token";
        Optional<ConnectedAccount> accountOpt = connectedAccountRepository.findByOrganizationIdAndChannel(organizationId, channel);
        if (accountOpt.isPresent()) {
            try {
                token = tokenEncryptionService.decrypt(accountOpt.get().getEncryptedAccessToken());
            } catch (Exception e) {
                log.warn("Decryption failed for channel account, using fallback: {}", e.getMessage());
            }
        }

        String externalMsgId = "agent_reply_" + System.currentTimeMillis();
        try {
            if (channel == ChannelType.INSTAGRAM) {
                if (mediaUrl != null && !mediaUrl.isBlank()) {
                    externalMsgId = instagramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
                } else {
                    externalMsgId = instagramChannelProvider.sendPrivateDirectMessage(token, recipientId, content);
                }
            } else if (channel == ChannelType.WHATSAPP) {
                if (mediaUrl != null && !mediaUrl.isBlank()) {
                    externalMsgId = whatsAppChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
                } else {
                    externalMsgId = whatsAppChannelProvider.sendMessage(token, recipientId, content);
                }
            } else if (channel == ChannelType.TELEGRAM) {
                if (mediaUrl != null && !mediaUrl.isBlank()) {
                    externalMsgId = telegramChannelProvider.sendMediaMessage(token, recipientId, "IMAGE", mediaUrl);
                } else {
                    externalMsgId = telegramChannelProvider.sendMessage(token, recipientId, content);
                }
            }
        } catch (Exception e) {
            log.error("Failed to dispatch live chat agent message through provider {}: {}", channel, e.getMessage());
            // In dev/mock mode or network failure, we still log and persist the message
        }

        String messageType = (mediaUrl != null && !mediaUrl.isBlank()) ? "MEDIA" : "TEXT";
        return recordMessage(organizationId, conversation, "OUTBOUND", "AGENT", messageType, content, mediaUrl, externalMsgId);
    }

    @Override
    @Transactional
    public Conversation resolveConversation(UUID organizationId, UUID conversationId, boolean resolved) {
        Conversation conversation = getConversationById(organizationId, conversationId);
        conversation.setResolved(resolved);
        Conversation saved = conversationRepository.save(conversation);
        liveChatStreamService.broadcastConversationResolved(organizationId, conversationId, resolved);
        log.info("Conversation [{}] resolved status updated to [{}] for org [{}]", conversationId, resolved, organizationId);
        return saved;
    }
}
