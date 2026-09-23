package com.autoflow.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

public class FunnelAnalyticsDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelStepMetric {
        private String nodeId;
        private String nodeLabel;
        private String nodeType;
        private int stepIndex;
        private long reachedCount;
        private long dropOffCount;
        private double conversionPercentage;     // (reachedCount / totalRuns) * 100
        private double stepConversionPercentage; // (reachedCount / prevStepReachedCount) * 100
        private double dropOffPercentage;        // (dropOffCount / reachedCount) * 100
        private long avgDurationMs;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowFunnelResponse {
        private UUID workflowId;
        private String workflowName;
        private String status;
        private long totalRuns;
        private long successfulRuns;
        private long failedRuns;
        private double overallConversionRate;
        private String bottleneckNodeId;
        private double bottleneckDropOffRate;
        private List<FunnelStepMetric> steps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelsOverviewResponse {
        private int totalPipelines;
        private long aggregateRuns;
        private long aggregateConversions;
        private double averageConversionRate;
        private UUID topConvertingWorkflowId;
        private String topConvertingWorkflowName;
        private List<WorkflowFunnelResponse> funnels;
    }
}
