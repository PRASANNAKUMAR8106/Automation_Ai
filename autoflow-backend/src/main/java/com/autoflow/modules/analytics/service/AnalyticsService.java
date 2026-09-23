package com.autoflow.modules.analytics.service;

import com.autoflow.modules.analytics.dto.AnalyticsOverviewResponse;
import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelsOverviewResponse;
import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.WorkflowFunnelResponse;

import java.util.UUID;

public interface AnalyticsService {

    AnalyticsOverviewResponse getOverview(UUID organizationId);

    WorkflowFunnelResponse getWorkflowFunnel(UUID organizationId, UUID workflowId);

    FunnelsOverviewResponse getFunnelsOverview(UUID organizationId);
}
