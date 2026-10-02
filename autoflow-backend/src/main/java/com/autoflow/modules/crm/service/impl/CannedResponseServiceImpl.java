package com.autoflow.modules.crm.service.impl;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.CannedResponse;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.repository.CannedResponseRepository;
import com.autoflow.modules.crm.repository.ConversationRepository;
import com.autoflow.modules.crm.service.CannedResponseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CannedResponseServiceImpl implements CannedResponseService {

    private final CannedResponseRepository cannedResponseRepository;
    private final ConversationRepository conversationRepository;

    @Override
    @Transactional
    public CannedResponse createCannedResponse(UUID organizationId, UUID userId, CannedResponseRequest request) {
        String normalizedShortcut = request.getShortcut().trim();
        if (!normalizedShortcut.startsWith("#") && !normalizedShortcut.startsWith("/")) {
            normalizedShortcut = "#" + normalizedShortcut;
        }

        Optional<CannedResponse> existing = cannedResponseRepository.findByOrganizationIdAndShortcut(organizationId, normalizedShortcut);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("A canned response with shortcut '" + normalizedShortcut + "' already exists");
        }

        CannedResponse snippet = CannedResponse.builder()
                .shortcut(normalizedShortcut)
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .category(request.getCategory() != null ? request.getCategory().trim().toUpperCase() : "GENERAL")
                .isShared(request.getIsShared() == null || request.getIsShared())
                .createdByUserId(userId)
                .usageCount(0)
                .build();
        snippet.setOrganizationId(organizationId);

        return cannedResponseRepository.save(snippet);
    }

    @Override
    @Transactional
    public CannedResponse updateCannedResponse(UUID organizationId, UUID id, CannedResponseRequest request) {
        CannedResponse snippet = cannedResponseRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CannedResponse", id));

        String normalizedShortcut = request.getShortcut().trim();
        if (!normalizedShortcut.startsWith("#") && !normalizedShortcut.startsWith("/")) {
            normalizedShortcut = "#" + normalizedShortcut;
        }

        Optional<CannedResponse> existing = cannedResponseRepository.findByOrganizationIdAndShortcut(organizationId, normalizedShortcut);
        if (existing.isPresent() && !existing.get().getId().equals(id)) {
            throw new IllegalArgumentException("A canned response with shortcut '" + normalizedShortcut + "' already exists");
        }

        snippet.setShortcut(normalizedShortcut);
        snippet.setTitle(request.getTitle().trim());
        snippet.setContent(request.getContent().trim());
        if (request.getCategory() != null) {
            snippet.setCategory(request.getCategory().trim().toUpperCase());
        }
        if (request.getIsShared() != null) {
            snippet.setShared(request.getIsShared());
        }

        return cannedResponseRepository.save(snippet);
    }

    @Override
    @Transactional
    public void deleteCannedResponse(UUID organizationId, UUID id) {
        CannedResponse snippet = cannedResponseRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("CannedResponse", id));
        cannedResponseRepository.delete(snippet);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CannedResponse> getCannedResponses(UUID organizationId, String category, String search) {
        if (search != null && !search.trim().isEmpty()) {
            return cannedResponseRepository.searchByKeyword(organizationId, search.trim());
        }
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("ALL")) {
            return cannedResponseRepository.findByOrganizationIdAndCategoryOrderByShortcutAsc(organizationId, category.trim().toUpperCase());
        }
        return cannedResponseRepository.findByOrganizationIdOrderByShortcutAsc(organizationId);
    }

    @Override
    @Transactional(readOnly = true)
    public String interpolateTemplate(UUID organizationId, UUID conversationId, String rawContent, String agentName) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }

        Conversation conversation = conversationRepository.findByIdAndOrganizationId(conversationId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        Contact contact = conversation.getContact();
        String contactName = "Customer";
        String contactUsername = "Customer";
        String contactEmail = "";
        String contactPhone = "";

        if (contact != null) {
            if (contact.getFullName() != null && !contact.getFullName().isBlank()) {
                contactName = contact.getFullName().trim();
            } else if (contact.getUsername() != null && !contact.getUsername().isBlank()) {
                contactName = contact.getUsername().trim();
            }
            if (contact.getUsername() != null) {
                contactUsername = contact.getUsername().trim();
            }
            if (contact.getEmail() != null) {
                contactEmail = contact.getEmail().trim();
            }
            if (contact.getPhone() != null) {
                contactPhone = contact.getPhone().trim();
            }
        }

        String resolvedAgent = (agentName != null && !agentName.isBlank()) ? agentName.trim() : "Support Team";
        String resolvedChannel = conversation.getChannel() != null ? conversation.getChannel().name() : "CHAT";

        return rawContent
                .replace("{{contact.name}}", contactName)
                .replace("{{contact.username}}", contactUsername)
                .replace("{{contact.email}}", contactEmail)
                .replace("{{contact.phone}}", contactPhone)
                .replace("{{agent.name}}", resolvedAgent)
                .replace("{{channel}}", resolvedChannel)
                .replace("{{organization.name}}", "AutoFlow");
    }

    @Override
    @Transactional
    public void trackUsage(UUID organizationId, UUID id) {
        cannedResponseRepository.incrementUsageCount(id, organizationId);
    }
}
