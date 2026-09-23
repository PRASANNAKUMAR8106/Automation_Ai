package com.autoflow.modules.crm.service;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.provider.instagram.InstagramChannelProvider;
import com.autoflow.modules.channel.provider.whatsapp.WhatsAppChannelProvider;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.entity.*;
import com.autoflow.modules.crm.repository.ContactRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CRM Contact & Messaging Service Tests")
class CrmServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConnectedAccountRepository connectedAccountRepository;

    @Mock
    private TokenEncryptionService tokenEncryptionService;

    @Mock
    private InstagramChannelProvider instagramChannelProvider;

    @Mock
    private WhatsAppChannelProvider whatsAppChannelProvider;

    @Mock
    private com.autoflow.modules.channel.provider.telegram.TelegramChannelProvider telegramChannelProvider;

    private CrmServiceImpl crmService;
    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        crmService = new CrmServiceImpl(
                contactRepository,
                conversationRepository,
                messageRepository,
                connectedAccountRepository,
                tokenEncryptionService,
                instagramChannelProvider,
                whatsAppChannelProvider,
                telegramChannelProvider
        );
    }

    @Test
    @DisplayName("Should create new contact when not existing")
    void shouldCreateNewContact() {
        when(contactRepository.findByOrganizationIdAndChannelAndExternalId(testOrgId, ChannelType.INSTAGRAM, "ig_user_1"))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> {
            Contact c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        Contact contact = crmService.getOrCreateContact(testOrgId, ChannelType.INSTAGRAM, "ig_user_1", "john_doe", "John Doe");

        assertNotNull(contact);
        assertEquals("ig_user_1", contact.getExternalId());
        assertEquals("john_doe", contact.getUsername());
        assertEquals(LeadStatus.NEW, contact.getLeadStatus());
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    @DisplayName("Should update existing contact on subsequent interaction")
    void shouldUpdateExistingContact() {
        Contact existing = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_user_1")
                .username("old_handle")
                .build();
        existing.setId(UUID.randomUUID());
        existing.setOrganizationId(testOrgId);

        when(contactRepository.findByOrganizationIdAndChannelAndExternalId(testOrgId, ChannelType.INSTAGRAM, "ig_user_1"))
                .thenReturn(Optional.of(existing));
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));

        Contact contact = crmService.getOrCreateContact(testOrgId, ChannelType.INSTAGRAM, "ig_user_1", "new_handle", "John Doe");

        assertEquals("new_handle", contact.getUsername());
        assertEquals("John Doe", contact.getFullName());
    }

    @Test
    @DisplayName("Should record outbound message and update conversation timestamp")
    void shouldRecordMessageAndUpdateConversation() {
        Contact contact = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_user_1").build();
        contact.setId(UUID.randomUUID());

        Conversation convo = Conversation.builder().organizationId(testOrgId).contact(contact).build();
        convo.setId(UUID.randomUUID());

        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Message msg = crmService.recordMessage(testOrgId, convo, "OUTBOUND", "BOT", "TEXT", "Hello from AutoFlow!", null, "mid_123");

        assertNotNull(msg);
        assertEquals("Hello from AutoFlow!", msg.getContent());
        assertEquals("OUTBOUND", msg.getDirection());
        verify(conversationRepository).save(convo);
    }

    @Test
    @DisplayName("Should add and deduplicate contact tags")
    void shouldAddAndDeduplicateTags() {
        UUID contactId = UUID.randomUUID();
        Contact contact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_user_1")
                .tags(new ArrayList<>(List.of("vip", "existing")))
                .build();
        contact.setId(contactId);
        contact.setOrganizationId(testOrgId);

        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));

        crmService.addTagsToContact(testOrgId, contactId, List.of("vip", "new_lead"));

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        List<String> updatedTags = captor.getValue().getTags();
        assertEquals(3, updatedTags.size());
        assertTrue(updatedTags.contains("vip"));
        assertTrue(updatedTags.contains("existing"));
        assertTrue(updatedTags.contains("new_lead"));
    }

    @Test
    @DisplayName("Should remove contact tag")
    void shouldRemoveContactTag() {
        UUID contactId = UUID.randomUUID();
        Contact contact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("ig_user_1")
                .tags(new ArrayList<>(List.of("vip", "existing", "promo")))
                .build();
        contact.setId(contactId);
        contact.setOrganizationId(testOrgId);

        when(contactRepository.findById(contactId)).thenReturn(Optional.of(contact));
        when(contactRepository.save(any(Contact.class))).thenAnswer(i -> i.getArgument(0));

        crmService.removeTagFromContact(testOrgId, contactId, "promo");

        ArgumentCaptor<Contact> captor = ArgumentCaptor.forClass(Contact.class);
        verify(contactRepository).save(captor.capture());
        List<String> updatedTags = captor.getValue().getTags();
        assertEquals(2, updatedTags.size());
        assertFalse(updatedTags.contains("promo"));
    }

    @Test
    @DisplayName("Should send agent live chat reply via Instagram channel provider")
    void shouldSendAgentReplyInstagram() {
        UUID convoId = UUID.randomUUID();
        Contact contact = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_recip_99").build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation convo = Conversation.builder()
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .contact(contact)
                .build();
        convo.setId(convoId);

        ConnectedAccount account = ConnectedAccount.builder()
                .channel(ChannelType.INSTAGRAM)
                .encryptedAccessToken("enc_token_123")
                .build();
        account.setOrganizationId(testOrgId);

        when(conversationRepository.findById(convoId)).thenReturn(Optional.of(convo));
        when(connectedAccountRepository.findByOrganizationIdAndChannel(testOrgId, ChannelType.INSTAGRAM))
                .thenReturn(Optional.of(account));
        when(tokenEncryptionService.decrypt("enc_token_123")).thenReturn("decrypted_token");
        when(instagramChannelProvider.sendPrivateDirectMessage(eq("decrypted_token"), eq("ig_recip_99"), eq("Hello, how can I help?")))
                .thenReturn("ig_msg_sent_777");
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Message sent = crmService.sendAgentReply(testOrgId, convoId, "Hello, how can I help?", null);

        assertNotNull(sent);
        assertEquals("AGENT", sent.getSenderType());
        assertEquals("OUTBOUND", sent.getDirection());
        assertEquals("ig_msg_sent_777", sent.getExternalMessageId());
        verify(instagramChannelProvider).sendPrivateDirectMessage("decrypted_token", "ig_recip_99", "Hello, how can I help?");
    }

    @Test
    @DisplayName("Should dispatch agent reply via Telegram channel")
    void shouldSendAgentReplyViaTelegram() {
        UUID convoId = UUID.randomUUID();
        Contact contact = Contact.builder()
                .channel(ChannelType.TELEGRAM)
                .externalId("123456789")
                .username("tg_customer")
                .build();
        contact.setId(UUID.randomUUID());
        contact.setOrganizationId(testOrgId);

        Conversation convo = Conversation.builder()
                .organizationId(testOrgId)
                .channel(ChannelType.TELEGRAM)
                .contact(contact)
                .build();
        convo.setId(convoId);

        ConnectedAccount account = ConnectedAccount.builder()
                .channel(ChannelType.TELEGRAM)
                .encryptedAccessToken("enc_tg_token_123")
                .build();
        account.setOrganizationId(testOrgId);

        when(conversationRepository.findById(convoId)).thenReturn(Optional.of(convo));
        when(connectedAccountRepository.findByOrganizationIdAndChannel(testOrgId, ChannelType.TELEGRAM))
                .thenReturn(Optional.of(account));
        when(tokenEncryptionService.decrypt("enc_tg_token_123")).thenReturn("bot_token_abc");
        when(telegramChannelProvider.sendMessage(eq("bot_token_abc"), eq("123456789"), eq("Hello on Telegram!")))
                .thenReturn("tg_msg_888");
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });
        when(conversationRepository.save(any(Conversation.class))).thenAnswer(i -> i.getArgument(0));

        Message sent = crmService.sendAgentReply(testOrgId, convoId, "Hello on Telegram!", null);

        assertNotNull(sent);
        assertEquals("AGENT", sent.getSenderType());
        assertEquals("OUTBOUND", sent.getDirection());
        assertEquals("tg_msg_888", sent.getExternalMessageId());
        verify(telegramChannelProvider).sendMessage("bot_token_abc", "123456789", "Hello on Telegram!");
    }
}
