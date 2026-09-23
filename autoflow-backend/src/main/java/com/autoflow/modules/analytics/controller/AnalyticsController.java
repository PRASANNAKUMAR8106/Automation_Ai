package com.autoflow.modules.analytics.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.analytics.dto.AnalyticsOverviewResponse;
import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.FunnelsOverviewResponse;
import com.autoflow.modules.analytics.dto.FunnelAnalyticsDto.WorkflowFunnelResponse;
import com.autoflow.modules.analytics.service.AnalyticsService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Performance analytics, engagement metrics, and funnel conversion tracking")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/overview")
    @Operation(summary = "Get Dashboard Analytics Overview", description = "Aggregates comment volume, DM delivery, captured leads, and quota usage")
    public ResponseEntity<ApiResponse<AnalyticsOverviewResponse>> getOverview() {
        UUID orgId = TenantContext.getRequiredTenantId();
        AnalyticsOverviewResponse overview = analyticsService.getOverview(orgId);
        return ResponseEntity.ok(ApiResponse.ok("Analytics overview retrieved successfully", overview));
    }

    @GetMapping("/funnels/{workflowId}")
    @Operation(summary = "Get Workflow Conversion Funnel", description = "Calculates node-by-node conversion rates, drop-offs, and identifies bottlenecks")
    public ResponseEntity<ApiResponse<WorkflowFunnelResponse>> getWorkflowFunnel(@PathVariable UUID workflowId) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowFunnelResponse funnel = analyticsService.getWorkflowFunnel(orgId, workflowId);
        return ResponseEntity.ok(ApiResponse.ok("Workflow funnel retrieved successfully", funnel));
    }

    @GetMapping("/funnels/overview")
    @Operation(summary = "Get Funnels Aggregate Overview", description = "Provides multi-workflow aggregate funnel performance across all active pipelines")
    public ResponseEntity<ApiResponse<FunnelsOverviewResponse>> getFunnelsOverview() {
        UUID orgId = TenantContext.getRequiredTenantId();
        FunnelsOverviewResponse overview = analyticsService.getFunnelsOverview(orgId);
        return ResponseEntity.ok(ApiResponse.ok("Funnels overview retrieved successfully", overview));
    }
}
