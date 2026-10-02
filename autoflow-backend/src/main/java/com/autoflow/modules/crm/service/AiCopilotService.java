package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionRequest;
import com.autoflow.modules.crm.dto.AiCopilotDto.AiSuggestionResponse;

import java.util.UUID;

public interface AiCopilotService {

    AiSuggestionResponse generateSuggestion(UUID organizationId, UUID conversationId, AiSuggestionRequest request);
}
