package com.autoflow.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsOverviewResponse {
    private long commentsProcessed;
    private long dmsDispatched;
    private long leadsCaptured;
    private long activeWorkflows;
    private int maxWorkflows;
    private long totalMessages;
    private long inboundMessages;
    private long outboundMessages;
    private Map<String, Long> channelBreakdown;
}
