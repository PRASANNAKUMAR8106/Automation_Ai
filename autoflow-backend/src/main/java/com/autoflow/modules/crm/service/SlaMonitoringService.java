package com.autoflow.modules.crm.service;

import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.entity.Conversation;

import java.util.List;
import java.util.UUID;

public interface SlaMonitoringService {

    SlaPolicyResponse createPolicy(UUID organizationId, SlaPolicyRequest request);

    List<SlaPolicyResponse> getPolicies(UUID organizationId);

    void applySlaPolicy(Conversation conversation);

    void recordFirstAgentReply(Conversation conversation);

    void recordResolution(Conversation conversation);

    void scanAndProcessBreaches();

    PerformanceAnalyticsResponse getPerformanceAnalytics(UUID organizationId);
}
