package com.autoflow.modules.influencer.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.influencer.dto.*;
import com.autoflow.modules.influencer.entity.Campaign;
import com.autoflow.modules.influencer.entity.Commission;
import com.autoflow.modules.influencer.entity.Influencer;
import com.autoflow.modules.influencer.entity.Payout;
import com.autoflow.modules.influencer.entity.PromoCode;
import com.autoflow.modules.influencer.service.InfluencerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping
@RequiredArgsConstructor
@Tag(name = "Influencer & Affiliate Management", description = "Admin controls and Influencer Read-Only Portal endpoints")
public class InfluencerController {

    private final InfluencerService influencerService;

    // =========================================================================
    // ADMIN ENDPOINTS (Strict Admin-Only Rule: Zero Influencer Write Access)
    // =========================================================================

    @PostMapping("/api/v1/admin/influencers")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Register Influencer (Admin Only)", description = "Registers and invites a new influencer partner")
    public ResponseEntity<ApiResponse<Influencer>> createInfluencer(
            @Valid @RequestBody CreateInfluencerRequest request
    ) {
        Influencer influencer = influencerService.createInfluencer(request);
        return ResponseEntity.ok(ApiResponse.ok("Influencer registered successfully", influencer));
    }

    @PostMapping("/api/v1/admin/influencer/campaigns")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create Campaign (Admin Only)", description = "Creates a commission campaign with gross or net qualifying basis")
    public ResponseEntity<ApiResponse<Campaign>> createCampaign(
            @Valid @RequestBody CreateCampaignRequest request
    ) {
        Campaign campaign = influencerService.createCampaign(request);
        return ResponseEntity.ok(ApiResponse.ok("Campaign created successfully", campaign));
    }

    @PostMapping("/api/v1/admin/influencer/promo-codes")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Issue Promo Code (Admin Only)", description = "Issues a branded promo discount and tracking code to an influencer")
    public ResponseEntity<ApiResponse<PromoCode>> createPromoCode(
            @Valid @RequestBody CreatePromoCodeRequest request
    ) {
        PromoCode promo = influencerService.createPromoCode(request);
        return ResponseEntity.ok(ApiResponse.ok("Promo code issued successfully", promo));
    }

    @PostMapping("/api/v1/admin/influencer/payouts/process")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
    @Operation(summary = "Process Payout (Admin Only)", description = "Approves and records payout disbursement to influencer")
    public ResponseEntity<ApiResponse<Payout>> processPayout(
            @Valid @RequestBody ProcessPayoutRequest request,
            Authentication authentication
    ) {
        UUID adminId = parseUserId(authentication);
        Payout payout = influencerService.processPayout(adminId, request);
        return ResponseEntity.ok(ApiResponse.ok("Payout processed successfully", payout));
    }

    // =========================================================================
    // INFLUENCER PORTAL ENDPOINTS (Strict Read-Only for Influencers)
    // =========================================================================

    @GetMapping("/api/v1/influencer/portal/stats")
    @Operation(summary = "Influencer Portal Statistics", description = "Returns clicks, conversions, pending, approved, and paid commissions")
    public ResponseEntity<ApiResponse<InfluencerStatsResponse>> getStats(Authentication authentication) {
        UUID userId = parseUserId(authentication);
        InfluencerStatsResponse stats = influencerService.getInfluencerStats(userId);
        return ResponseEntity.ok(ApiResponse.ok("Influencer stats retrieved", stats));
    }

    @GetMapping("/api/v1/influencer/portal/commissions")
    @Operation(summary = "Influencer Commission Ledger", description = "Returns historical commission earnings statement")
    public ResponseEntity<ApiResponse<List<Commission>>> getCommissions(Authentication authentication) {
        UUID userId = parseUserId(authentication);
        List<Commission> commissions = influencerService.getInfluencerCommissions(userId);
        return ResponseEntity.ok(ApiResponse.ok("Commissions retrieved", commissions));
    }

    private UUID parseUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() != null) {
            try {
                return UUID.fromString(authentication.getPrincipal().toString());
            } catch (Exception ignored) {
            }
        }
        return UUID.randomUUID();
    }
}
