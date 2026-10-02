package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.AgentProductivityDto.*;
import com.autoflow.modules.crm.entity.CannedResponse;

import java.util.List;
import java.util.UUID;

public interface CannedResponseService {

    CannedResponse createCannedResponse(UUID organizationId, UUID userId, CannedResponseRequest request);

    CannedResponse updateCannedResponse(UUID organizationId, UUID id, CannedResponseRequest request);

    void deleteCannedResponse(UUID organizationId, UUID id);

    List<CannedResponse> getCannedResponses(UUID organizationId, String category, String search);

    String interpolateTemplate(UUID organizationId, UUID conversationId, String rawContent, String agentName);

    void trackUsage(UUID organizationId, UUID id);
}
