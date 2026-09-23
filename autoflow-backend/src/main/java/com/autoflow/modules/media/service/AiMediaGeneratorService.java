package com.autoflow.modules.media.service;

import com.autoflow.modules.media.dto.AiMediaGenerateRequest;

import java.util.UUID;

public interface AiMediaGeneratorService {

    /**
     * Synthesizes a high-resolution, branded visual asset according to the request
     * and persists it via MediaStorageService with tenant isolation.
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
