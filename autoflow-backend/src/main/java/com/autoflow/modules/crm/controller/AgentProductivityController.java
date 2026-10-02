package com.autoflow.modules.crm.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.CannedResponse;
import com.autoflow.modules.crm.entity.CrmMacro;
import com.autoflow.modules.crm.service.*;
import com.autoflow.modules.user.entity.User;
import com.autoflow.modules.user.repository.UserRepository;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
@Tag(name = "Agent Productivity & Collaboration", description = "Canned responses, collision detection, internal notes, macros, and conversation timeline")
public class AgentProductivityController {

    private final CannedResponseService cannedResponseService;
    private final AgentCollisionService agentCollisionService;
    private final InternalNoteService internalNoteService;
    private final MacroExecutionService macroExecutionService;
    private final ConversationTimelineService conversationTimelineService;
    private final UserRepository userRepository;

    // --- 1. CANNED RESPONSES ---

    @GetMapping("/canned-responses")
    @Operation(summary = "List Canned Responses", description = "Returns available canned responses with optional category and keyword search")
    public ResponseEntity<ApiResponse<List<CannedResponseResponse>>> getCannedResponses(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<CannedResponse> snippets = cannedResponseService.getCannedResponses(organizationId, category, search);
        List<CannedResponseResponse> response = snippets.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Canned responses retrieved", response));
    }

    @PostMapping("/canned-responses")
    @Operation(summary = "Create Canned Response", description = "Creates a new reusable canned response snippet with shortcut")
    public ResponseEntity<ApiResponse<CannedResponseResponse>> createCannedResponse(
            @Valid @RequestBody CannedResponseRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        CannedResponse created = cannedResponseService.createCannedResponse(organizationId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Canned response created", toResponse(created)));
    }

    @PutMapping("/canned-responses/{id}")
    @Operation(summary = "Update Canned Response", description = "Updates an existing canned response snippet")
    public ResponseEntity<ApiResponse<CannedResponseResponse>> updateCannedResponse(
            @PathVariable UUID id,
            @Valid @RequestBody CannedResponseRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        CannedResponse updated = cannedResponseService.updateCannedResponse(organizationId, id, request);
        return ResponseEntity.ok(ApiResponse.ok("Canned response updated", toResponse(updated)));
    }

    @DeleteMapping("/canned-responses/{id}")
    @Operation(summary = "Delete Canned Response", description = "Deletes a canned response snippet")
    public ResponseEntity<ApiResponse<Void>> deleteCannedResponse(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        cannedResponseService.deleteCannedResponse(organizationId, id);
        return ResponseEntity.ok(ApiResponse.ok("Canned response deleted", null));
    }

    @PostMapping("/conversations/{id}/interpolate-template")
    @Operation(summary = "Interpolate Template Variables", description = "Replaces placeholders in snippet text using live contact profile attributes")
    public ResponseEntity<ApiResponse<InterpolatedResponse>> interpolateSnippet(
            @PathVariable UUID id,
            @Valid @RequestBody InterpolateRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        String currentEmail = getCurrentUserEmail();
        String interpolated = cannedResponseService.interpolateTemplate(organizationId, id, request.getContent(), currentEmail);
        return ResponseEntity.ok(ApiResponse.ok("Interpolated successfully", InterpolatedResponse.builder()
                .originalContent(request.getContent())
                .interpolatedContent(interpolated)
                .build()));
    }

    // --- 2. AGENT COLLISION & PRESENCE ---

    @PostMapping("/conversations/{id}/presence")
    @Operation(summary = "Record Agent Presence Heartbeat", description = "Signals that an agent is viewing or typing in a conversation thread")
    public ResponseEntity<ApiResponse<List<AgentPresenceDto>>> recordPresence(
            @PathVariable UUID id,
            @Valid @RequestBody AgentPresenceHeartbeatRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        String userEmail = request.getUserEmail() != null ? request.getUserEmail() : getCurrentUserEmail();
        List<AgentPresenceDto> active = agentCollisionService.recordPresence(organizationId, id, userId, userEmail, request.getAction());
        return ResponseEntity.ok(ApiResponse.ok("Presence recorded", active));
    }

    @DeleteMapping("/conversations/{id}/presence")
    @Operation(summary = "Release Agent Presence", description = "Explicitly signals that an agent has navigated away from a conversation")
    public ResponseEntity<ApiResponse<List<AgentPresenceDto>>> releasePresence(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        List<AgentPresenceDto> active = agentCollisionService.releasePresence(organizationId, id, userId);
        return ResponseEntity.ok(ApiResponse.ok("Presence released", active));
    }

    @GetMapping("/conversations/{id}/presence")
    @Operation(summary = "Get Active Viewers", description = "Returns active agents currently viewing or typing in the conversation")
    public ResponseEntity<ApiResponse<List<AgentPresenceDto>>> getActivePresence(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<AgentPresenceDto> active = agentCollisionService.getActiveViewers(organizationId, id);
        return ResponseEntity.ok(ApiResponse.ok("Active presence list retrieved", active));
    }

    // --- 3. INTERNAL NOTES & SUPERVISOR WHISPERS ---

    @PostMapping("/conversations/{id}/notes")
    @Operation(summary = "Post Internal Note / Whisper", description = "Adds a team-internal note or supervisor whisper (firewalled from customer)")
    public ResponseEntity<ApiResponse<InternalNoteResponse>> postInternalNote(
            @PathVariable UUID id,
            @Valid @RequestBody PostInternalNoteRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        String email = getCurrentUserEmail();
        InternalNoteResponse note = internalNoteService.createInternalNote(organizationId, id, userId, email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Internal note posted", note));
    }

    @GetMapping("/conversations/{id}/notes")
    @Operation(summary = "Get Internal Notes", description = "Fetches team internal notes and supervisor whispers for a conversation")
    public ResponseEntity<ApiResponse<List<InternalNoteResponse>>> getInternalNotes(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<InternalNoteResponse> notes = internalNoteService.getInternalNotes(organizationId, id);
        return ResponseEntity.ok(ApiResponse.ok("Internal notes retrieved", notes));
    }

    // --- 4. CRM MACROS ---

    @GetMapping("/macros")
    @Operation(summary = "List Macros", description = "Lists all multi-action macros available for the organization")
    public ResponseEntity<ApiResponse<List<CrmMacroResponse>>> getMacros() {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<CrmMacro> macros = macroExecutionService.getMacros(organizationId);
        List<CrmMacroResponse> response = macros.stream().map(this::toMacroResponse).toList();
        return ResponseEntity.ok(ApiResponse.ok("Macros retrieved", response));
    }

    @PostMapping("/macros")
    @Operation(summary = "Create Macro", description = "Creates a new multi-action macro template")
    public ResponseEntity<ApiResponse<CrmMacroResponse>> createMacro(
            @Valid @RequestBody CrmMacroRequest request
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        CrmMacro created = macroExecutionService.createMacro(organizationId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Macro created", toMacroResponse(created)));
    }

    @DeleteMapping("/macros/{id}")
    @Operation(summary = "Delete Macro", description = "Deletes a macro template")
    public ResponseEntity<ApiResponse<Void>> deleteMacro(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        macroExecutionService.deleteMacro(organizationId, id);
        return ResponseEntity.ok(ApiResponse.ok("Macro deleted", null));
    }

    @PostMapping("/conversations/{id}/apply-macro/{macroId}")
    @Operation(summary = "Apply Macro to Conversation", description = "Atomically executes macro actions against conversation and contact")
    public ResponseEntity<ApiResponse<ApplyMacroResult>> applyMacro(
            @PathVariable UUID id,
            @PathVariable UUID macroId
    ) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        UUID userId = getCurrentUserId();
        String agentEmail = getCurrentUserEmail();
        ApplyMacroResult result = macroExecutionService.applyMacro(organizationId, id, macroId, userId, agentEmail);
        return ResponseEntity.ok(ApiResponse.ok("Macro applied", result));
    }

    // --- 5. UNIFIED CONVERSATION TIMELINE ---

    @GetMapping("/conversations/{id}/timeline")
    @Operation(summary = "Get Unified Conversation Timeline", description = "Chronological audit ledger aggregating messages, notes, SLA events, AI actions, and CSAT")
    public ResponseEntity<ApiResponse<List<TimelineEventDto>>> getTimeline(@PathVariable UUID id) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<TimelineEventDto> timeline = conversationTimelineService.getConversationTimeline(organizationId, id);
        return ResponseEntity.ok(ApiResponse.ok("Conversation timeline retrieved", timeline));
    }

    // --- Helpers ---

    private CannedResponseResponse toResponse(CannedResponse c) {
        return CannedResponseResponse.builder()
                .id(c.getId())
                .shortcut(c.getShortcut())
                .title(c.getTitle())
                .content(c.getContent())
                .category(c.getCategory())
                .isShared(c.isShared())
                .createdByUserId(c.getCreatedByUserId())
                .usageCount(c.getUsageCount())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private CrmMacroResponse toMacroResponse(CrmMacro m) {
        return CrmMacroResponse.builder()
                .id(m.getId())
                .name(m.getName())
                .description(m.getDescription())
                .actionsJson(m.getActionsJson())
                .createdByUserId(m.getCreatedByUserId())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            Optional<User> user = userRepository.findByEmailIgnoreCase(auth.getName());
            if (user.isPresent()) {
                return user.get().getId();
            }
        }
        return UUID.randomUUID(); // Fallback for testing environments
    }

    private String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null && !auth.getName().equalsIgnoreCase("anonymousUser")) {
            return auth.getName();
        }
        return "agent@autoflow.ai";
    }
}
