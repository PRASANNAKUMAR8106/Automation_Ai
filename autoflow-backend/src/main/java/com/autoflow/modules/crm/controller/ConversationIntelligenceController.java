package com.autoflow.modules.crm.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.security.TenantContext;
import com.autoflow.modules.crm.dto.ConversationIntelligenceDto.*;
import com.autoflow.modules.crm.service.CsatService;
import com.autoflow.modules.crm.service.SlaMonitoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/crm")
@RequiredArgsConstructor
public class ConversationIntelligenceController {

    private final SlaMonitoringService slaMonitoringService;
    private final CsatService csatService;

    @GetMapping("/sla/policies")
    public ResponseEntity<ApiResponse<List<SlaPolicyResponse>>> getSlaPolicies() {
        UUID organizationId = TenantContext.getRequiredTenantId();
        List<SlaPolicyResponse> policies = slaMonitoringService.getPolicies(organizationId);
        return ResponseEntity.ok(ApiResponse.ok(policies));
    }

    @PostMapping("/sla/policies")
    public ResponseEntity<ApiResponse<SlaPolicyResponse>> createSlaPolicy(
            @Valid @RequestBody SlaPolicyRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        SlaPolicyResponse policy = slaMonitoringService.createPolicy(organizationId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Policy created successfully", policy));
    }

    @GetMapping("/analytics/performance")
    public ResponseEntity<ApiResponse<PerformanceAnalyticsResponse>> getPerformanceAnalytics() {
        UUID organizationId = TenantContext.getRequiredTenantId();
        PerformanceAnalyticsResponse analytics = slaMonitoringService.getPerformanceAnalytics(organizationId);
        return ResponseEntity.ok(ApiResponse.ok(analytics));
    }

    @PostMapping("/conversations/{conversationId}/csat")
    public ResponseEntity<ApiResponse<CsatSurveyResponse>> submitCsatFeedback(
            @PathVariable UUID conversationId,
            @Valid @RequestBody SubmitCsatRequest request) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        CsatSurveyResponse survey = csatService.submitFeedback(organizationId, conversationId, request);
        return ResponseEntity.ok(ApiResponse.ok(survey));
    }

    @GetMapping("/conversations/{conversationId}/csat")
    public ResponseEntity<ApiResponse<CsatSurveyResponse>> getCsatSurvey(
            @PathVariable UUID conversationId) {
        UUID organizationId = TenantContext.getRequiredTenantId();
        CsatSurveyResponse survey = csatService.getSurvey(organizationId, conversationId);
        return ResponseEntity.ok(ApiResponse.ok(survey));
    }
}
