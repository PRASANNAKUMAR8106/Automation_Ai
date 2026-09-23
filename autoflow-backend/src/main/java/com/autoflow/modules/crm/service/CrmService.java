package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;

import java.util.List;
import java.util.UUID;

public interface CrmService {

    Contact getOrCreateContact(UUID organizationId, ChannelType channel, String externalId, String username, String fullName);

    Conversation getOrCreateConversation(UUID organizationId, Contact contact);

    Message recordMessage(UUID organizationId, Conversation conversation, String direction, String senderType, String messageType, String content, String mediaUrl, String externalMessageId);

    void addTagsToContact(UUID organizationId, UUID contactId, List<String> tags);

    void removeTagFromContact(UUID organizationId, UUID contactId, String tag);

    Contact getContactById(UUID organizationId, UUID contactId);

    List<Contact> getContacts(UUID organizationId, String search, String tag);

    List<Contact> getContacts(UUID organizationId);

    Conversation getConversationById(UUID organizationId, UUID conversationId);

    List<Conversation> getConversations(UUID organizationId);

    List<Message> getMessages(UUID organizationId, UUID conversationId);

    Message sendAgentReply(UUID organizationId, UUID conversationId, String content, String mediaUrl);

    Message sendAgentReply(UUID organizationId, UUID conversationId, String content, String mediaUrl, boolean humanAgentTag);

    Conversation resolveConversation(UUID organizationId, UUID conversationId, boolean resolved);
}
