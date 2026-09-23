package com.autoflow.modules.media.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.media.entity.MediaAsset;
import com.autoflow.modules.media.service.MediaStorageService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
@Tag(name = "Media & Asset Storage", description = "Secure file uploads with binary MIME magic-byte validation and pre-signed S3 links")
public class MediaController {

    private static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    private final MediaStorageService mediaStorageService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload Media Asset", description = "Uploads file with mandatory binary magic-byte inspection and SHA-256 integrity verification")
    public ResponseEntity<ApiResponse<MediaStorageService.MediaUploadResponse>> uploadFile(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        UUID orgId = TenantContext.getRequiredTenantId();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot upload empty file");
        }

        MediaStorageService.MediaUploadResponse response = mediaStorageService.uploadFile(
                orgId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                MAX_FILE_SIZE_BYTES
        );

        return ResponseEntity.ok(ApiResponse.ok("File uploaded and verified successfully", response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Media Asset Metadata", description = "Retrieves tenant-isolated media asset metadata and short-lived pre-signed download URL")
    public ResponseEntity<ApiResponse<MediaStorageService.MediaUploadResponse>> getMediaMetadata(
            @PathVariable UUID id
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        MediaAsset asset = mediaStorageService.getMediaAsset(orgId, id);
        String downloadUrl = mediaStorageService.generatePresignedDownloadUrl(orgId, id, Duration.ofHours(1));
        MediaStorageService.MediaUploadResponse response = new MediaStorageService.MediaUploadResponse(
                asset.getId(),
                asset.getOrganizationId(),
                asset.getFileName(),
                asset.getMimeType(),
                asset.getFileSizeBytes(),
                asset.getSha256Checksum(),
                downloadUrl
        );
        return ResponseEntity.ok(ApiResponse.ok("Media asset metadata retrieved", response));
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Get Pre-Signed Download URL", description = "Generates secure pre-signed S3 download URL with bounded TTL (1-1440 mins)")
    public ResponseEntity<ApiResponse<Map<String, String>>> getDownloadUrl(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "60") int ttlMinutes
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        int safeTtl = Math.min(Math.max(ttlMinutes, 1), 1440);
        String url = mediaStorageService.generatePresignedDownloadUrl(orgId, id, Duration.ofMinutes(safeTtl));
        return ResponseEntity.ok(ApiResponse.ok("Download URL generated", Map.of("downloadUrl", url)));
    }
}
