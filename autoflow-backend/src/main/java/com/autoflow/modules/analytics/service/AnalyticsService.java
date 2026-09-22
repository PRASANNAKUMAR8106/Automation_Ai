package com.autoflow.modules.analytics.service;

import com.autoflow.modules.analytics.dto.AnalyticsOverviewResponse;

import java.util.UUID;

public interface AnalyticsService {

    AnalyticsOverviewResponse getOverview(UUID organizationId);
}
