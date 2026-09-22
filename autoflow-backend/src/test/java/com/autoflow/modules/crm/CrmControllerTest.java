package com.autoflow.modules.crm;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.crm.controller.CrmController;
import com.autoflow.modules.crm.dto.CrmDto.*;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.security.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = CrmController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("CrmController Web MVC Tests")
class CrmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CrmService crmService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/crm/contacts returns list of contacts")
    void testGetContacts() throws Exception {
        Contact contact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("user_101")
                .username("sarah_fit")
                .fullName("Sarah Fitness")
                .tags(List.of("lead", "vip"))
                .build();
        contact.setId(UUID.randomUUID());

        when(crmService.getContacts(eq(testOrgId), any(), any())).thenReturn(List.of(contact));

        mockMvc.perform(get("/api/v1/crm/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].username").value("sarah_fit"))
                .andExpect(jsonPath("$.data[0].channel").value("INSTAGRAM"));
    }

    @Test
    @DisplayName("GET /api/v1/crm/contacts/{id} returns single contact")
    void testGetContactById() throws Exception {
        UUID contactId = UUID.randomUUID();
        Contact contact = Contact.builder()
                .channel(ChannelType.WHATSAPP)
                .externalId("+15550100")
                .username("+15550100")
                .fullName("Alex Customer")
                .tags(List.of("whatsapp_lead"))
                .build();
        contact.setId(contactId);

        when(crmService.getContactById(testOrgId, contactId)).thenReturn(contact);

        mockMvc.perform(get("/api/v1/crm/contacts/" + contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Alex Customer"))
                .andExpect(jsonPath("$.data.channel").value("WHATSAPP"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/contacts/{id}/tags appends tags")
    void testAddTags() throws Exception {
        UUID contactId = UUID.randomUUID();
        AddTagsRequest request = new AddTagsRequest(List.of("hot_lead", "closed_won"));

        Contact updated = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("user_101")
                .tags(List.of("lead", "hot_lead", "closed_won"))
                .build();
        updated.setId(contactId);

        when(crmService.getContactById(testOrgId, contactId)).thenReturn(updated);

        mockMvc.perform(post("/api/v1/crm/contacts/" + contactId + "/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tags.length()").value(3));

        verify(crmService).addTagsToContact(testOrgId, contactId, request.getTags());
    }

    @Test
    @DisplayName("DELETE /api/v1/crm/contacts/{id}/tags/{tag} removes tag")
    void testRemoveTag() throws Exception {
        UUID contactId = UUID.randomUUID();
        Contact updated = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("user_101")
                .tags(List.of("lead"))
                .build();
        updated.setId(contactId);

        when(crmService.getContactById(testOrgId, contactId)).thenReturn(updated);

        mockMvc.perform(delete("/api/v1/crm/contacts/" + contactId + "/tags/vip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(crmService).removeTagFromContact(testOrgId, contactId, "vip");
    }

    @Test
    @DisplayName("GET /api/v1/crm/conversations returns thread directory")
    void testGetConversations() throws Exception {
        UUID convoId = UUID.randomUUID();
        Contact contact = Contact.builder()
                .channel(ChannelType.INSTAGRAM)
                .externalId("user_101")
                .username("sarah_fit")
                .build();
        contact.setId(UUID.randomUUID());

        Conversation convo = Conversation.builder()
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .contact(contact)
                .lastMessageAt(Instant.now())
                .build();
        convo.setId(convoId);

        Message msg = Message.builder()
                .content("Thanks for the info!")
                .direction("INBOUND")
                .senderType("CONTACT")
                .sentAt(Instant.now())
                .build();

        when(crmService.getConversations(testOrgId)).thenReturn(List.of(convo));
        when(crmService.getMessages(testOrgId, convoId)).thenReturn(List.of(msg));

        mockMvc.perform(get("/api/v1/crm/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].contact.username").value("sarah_fit"))
                .andExpect(jsonPath("$.data[0].lastMessageSnippet").value("Thanks for the info!"));
    }

    @Test
    @DisplayName("POST /api/v1/crm/conversations/{id}/messages sends agent live chat reply")
    void testSendAgentReply() throws Exception {
        UUID convoId = UUID.randomUUID();
        SendAgentReplyRequest req = new SendAgentReplyRequest("Hi Sarah, here is your coupon code!", null);

        Message sent = Message.builder()
                .content(req.getContent())
                .direction("OUTBOUND")
                .senderType("AGENT")
                .messageType("TEXT")
                .deliveryStatus("DELIVERED")
                .sentAt(Instant.now())
                .build();
        sent.setId(UUID.randomUUID());

        when(crmService.sendAgentReply(testOrgId, convoId, req.getContent(), null)).thenReturn(sent);

        mockMvc.perform(post("/api/v1/crm/conversations/" + convoId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.senderType").value("AGENT"))
                .andExpect(jsonPath("$.data.content").value("Hi Sarah, here is your coupon code!"));
    }
}
