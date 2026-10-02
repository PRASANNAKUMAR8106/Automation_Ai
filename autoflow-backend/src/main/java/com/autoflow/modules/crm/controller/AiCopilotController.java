package com.autoflow.modules.crm.controller;

import com.autoflow.security.TenantContext;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionRequest;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionResponse;
import com.autoflow.modules.crm.service.AiCopilotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/crm/conversations")
@RequiredArgsConstructor
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;

    @PostMapping("/{conversationId}/ai-suggest")
    public ResponseEntity<AiSuggestionResponse> getAiSuggestion(
            @PathVariable UUID conversationId,
            @RequestBody(required = false) AiSuggestionRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        AiSuggestionResponse response = aiCopilotService.generateSuggestion(orgId, conversationId, request);
        return ResponseEntity.ok(response);
    }
}
