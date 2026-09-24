package com.autoflow.modules.campaign.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.campaign.dto.CampaignDto.*;
import com.autoflow.modules.campaign.entity.BroadcastCampaignStatus;
import com.autoflow.modules.campaign.service.CampaignService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaigns & Broadcasts", description = "Scheduled omnichannel broadcast campaigns, audience segmentation, and delivery analytics")
public class CampaignController {

    private final CampaignService campaignService;

    @PostMapping("/estimate-audience")
    @Operation(summary = "Estimate Campaign Audience Reach", description = "Calculates total matching CRM contacts and Meta 24-hour window eligibility before scheduling")
    public ResponseEntity<ApiResponse<AudienceEstimateResponse>> estimateAudience(
            @Valid @RequestBody AudienceEstimateRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        AudienceEstimateResponse estimate = campaignService.estimateAudience(orgId, request);
        return ResponseEntity.ok(ApiResponse.ok("Audience reach calculated successfully", estimate));
    }

    @PostMapping
    @Operation(summary = "Create & Schedule Campaign", description = "Creates a new broadcast campaign and schedules recipient batches")
    public ResponseEntity<ApiResponse<CampaignResponse>> createCampaign(
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        CampaignResponse campaign = campaignService.createCampaign(orgId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Campaign scheduled successfully", campaign));
    }

    @GetMapping
    @Operation(summary = "List Campaigns", description = "Retrieves paginated list of broadcast campaigns for the tenant organization")
    public ResponseEntity<ApiResponse<Page<CampaignResponse>>> getCampaigns(
            @RequestParam(required = false) BroadcastCampaignStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100));
        Page<CampaignResponse> campaigns = campaignService.getCampaigns(orgId, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Campaigns retrieved successfully", campaigns));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Campaign Details & Delivery Metrics", description = "Retrieves campaign metrics and recent recipient delivery logs")
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> getCampaignDetail(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        CampaignDetailResponse detail = campaignService.getCampaignDetail(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Campaign details retrieved successfully", detail));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel Campaign", description = "Cancels a scheduled or pending broadcast campaign")
    public ResponseEntity<ApiResponse<CampaignResponse>> cancelCampaign(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        CampaignResponse cancelled = campaignService.cancelCampaign(orgId, id);
        return ResponseEntity.ok(ApiResponse.ok("Campaign cancelled successfully", cancelled));
    }
}
