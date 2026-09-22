package com.autoflow.modules.crm.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.security.TenantContext;
import com.autoflow.modules.crm.dto.CrmDto.*;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.entity.Message;
import com.autoflow.modules.crm.service.CrmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@Tag(name = "CRM & Live Chat Inbox", description = "Unified inbox, contact management, and agent messaging")
public class CrmController {

    private final CrmService crmService;

    @GetMapping("/contacts")
    @Operation(summary = "List Contacts", description = "Query CRM contacts with optional keyword search and tag filter")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> getContacts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String tag
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<Contact> contacts = crmService.getContacts(orgId, search, tag);
        List<ContactResponse> response = contacts.stream().map(this::toContactResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Contacts retrieved successfully", response));
    }

    @GetMapping("/contacts/{id}")
    @Operation(summary = "Get Contact Details", description = "Retrieves full contact profile and tags")
    public ResponseEntity<ApiResponse<ContactResponse>> getContact(@PathVariable UUID id) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Contact contact = crmService.getContactById(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Contact retrieved successfully", toContactResponse(contact)));
    }

    @PostMapping("/contacts/{id}/tags")
    @Operation(summary = "Add Tags to Contact", description = "Appends tags to a contact profile")
    public ResponseEntity<ApiResponse<ContactResponse>> addTags(
            @PathVariable UUID id,
            @Valid @RequestBody AddTagsRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        crmService.addTagsToContact(orgId, id, request.getTags());
        Contact contact = crmService.getContactById(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Tags added successfully", toContactResponse(contact)));
    }

    @DeleteMapping("/contacts/{id}/tags/{tag}")
    @Operation(summary = "Remove Tag from Contact", description = "Deletes a tag from a contact profile")
    public ResponseEntity<ApiResponse<ContactResponse>> removeTag(
            @PathVariable UUID id,
            @PathVariable String tag
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        crmService.removeTagFromContact(orgId, id, tag);
        Contact contact = crmService.getContactById(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Tag removed successfully", toContactResponse(contact)));
    }

    @GetMapping("/conversations")
    @Operation(summary = "List Conversations", description = "Lists all customer conversation threads in descending recency order")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations() {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<Conversation> conversations = crmService.getConversations(orgId);

        List<ConversationResponse> response = conversations.stream().map(c -> {
            List<Message> messages = crmService.getMessages(orgId, c.getId());
            String lastSnippet = messages.isEmpty() ? "" : messages.get(messages.size() - 1).getContent();
            return toConversationResponse(c, lastSnippet);
        }).toList();

        return ResponseEntity.ok(ApiResponse.ok("Conversations retrieved successfully", response));
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Get Conversation Messages", description = "Fetches message thread history for a conversation")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(@PathVariable UUID id) {
        UUID orgId = TenantContext.getRequiredTenantId();
        List<Message> messages = crmService.getMessages(orgId, id);
        List<MessageResponse> response = messages.stream().map(this::toMessageResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Messages retrieved successfully", response));
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Send Live Chat Agent Reply", description = "Dispatches human agent message across social channels and records in thread")
    public ResponseEntity<ApiResponse<MessageResponse>> sendAgentReply(
            @PathVariable UUID id,
            @Valid @RequestBody SendAgentReplyRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Message sent = crmService.sendAgentReply(orgId, id, request.getContent(), request.getMediaUrl());
        return ResponseEntity.ok(ApiResponse.ok("Agent reply sent successfully", toMessageResponse(sent)));
    }

    private ContactResponse toContactResponse(Contact c) {
        return ContactResponse.builder()
                .id(c.getId())
                .channel(c.getChannel())
                .externalId(c.getExternalId())
                .username(c.getUsername())
                .fullName(c.getFullName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .leadStatus(c.getLeadStatus())
                .leadScore(c.getLeadScore())
                .tags(c.getTags())
                .lastInteractionAt(c.getLastInteractionAt())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private ConversationResponse toConversationResponse(Conversation c, String lastSnippet) {
        return ConversationResponse.builder()
                .id(c.getId())
                .contact(toContactResponse(c.getContact()))
                .channel(c.getChannel())
                .isResolved(c.isResolved())
                .lastMessageAt(c.getLastMessageAt())
                .lastMessageSnippet(lastSnippet)
                .unreadCount(0)
                .build();
    }

    private MessageResponse toMessageResponse(Message m) {
        return MessageResponse.builder()
                .id(m.getId())
                .conversationId(m.getConversation() != null ? m.getConversation().getId() : null)
                .direction(m.getDirection())
                .senderType(m.getSenderType())
                .messageType(m.getMessageType())
                .content(m.getContent())
                .mediaUrl(m.getMediaUrl())
                .deliveryStatus(m.getDeliveryStatus())
                .sentAt(m.getSentAt())
                .build();
    }
}
