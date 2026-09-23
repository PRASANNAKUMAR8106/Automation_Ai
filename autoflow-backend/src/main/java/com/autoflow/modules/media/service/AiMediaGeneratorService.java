package com.autoflow.modules.media.service;

import com.autoflow.modules.media.dto.AiMediaGenerateRequest;

import java.util.UUID;

/**
 * Service for synthesizing branded visual assets using template-based Java 2D graphics rendering.
 * Provides programmatic image generation (coupons, vouchers, banners) with dynamic token interpolation
 * without requiring heavy native external image generation binaries or third-party AI image APIs.
 */
public interface AiMediaGeneratorService {

    /**
     * Synthesizes a high-resolution, branded visual asset using template-based Java 2D graphics rendering
     * according to the request and persists it via MediaStorageService with strict tenant isolation.
     *
     * @param orgId Organization/Tenant ID
     * @param request Generation parameters
     * @return Upload and storage response with signed URL
     */
    MediaStorageService.MediaUploadResponse generateBrandedAsset(
            UUID orgId,
            AiMediaGenerateRequest request
    );
}
