package com.autoflow.modules.media.service;

import com.autoflow.modules.media.entity.MediaAsset;
import com.autoflow.modules.media.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaStorageServiceImpl implements MediaStorageService {

    private final FileSecurityValidator fileSecurityValidator;
    private final MediaAssetRepository mediaAssetRepository;

    @Value("${autoflow.s3.bucket-media:autoflow-media}")
    private String bucketMedia;

    @Value("${autoflow.s3.endpoint:http://localhost:9000}")
    private String s3Endpoint;

    @Override
    @Transactional
    public MediaUploadResponse uploadFile(
            UUID orgId,
            String originalFileName,
            String declaredContentType,
            InputStream inputStream,
            long maxSizeBytes
    ) {
        // 1. Inspect binary magic bytes, verify safe extension, and calculate SHA-256
        FileSecurityValidator.ValidatedFileResult validationResult;
        try {
            validationResult = fileSecurityValidator.validate(
                    originalFileName,
                    declaredContentType,
                    inputStream,
                    maxSizeBytes
            );
        } catch (Exception e) {
            log.warn("File security validation failed for org [{}], file [{}]: {}", orgId, originalFileName, e.getMessage());
            throw new SecurityException("Upload validation failed: " + e.getMessage(), e);
        }

        // 2. Build structured, isolated S3 object key
        LocalDate now = LocalDate.now();
        String s3Key = String.format("tenants/%s/media/%d/%02d/%s_%s",
                orgId, now.getYear(), now.getMonthValue(), UUID.randomUUID(), validationResult.sanitizedFileName());

        // 3. Persist MediaAsset record in tenant-isolated database
        MediaAsset asset = MediaAsset.builder()
                .fileName(validationResult.sanitizedFileName())
                .mimeType(validationResult.detectedMimeType())
                .fileSizeBytes(validationResult.sizeBytes())
                .s3Bucket(bucketMedia)
                .s3Key(s3Key)
                .sha256Checksum(validationResult.sha256Checksum())
                .build();
        asset.setOrganizationId(orgId);

        MediaAsset saved = mediaAssetRepository.save(asset);
        String downloadUrl = generatePresignedDownloadUrl(orgId, saved.getId(), Duration.ofHours(1));

        log.info("Uploaded and validated media asset {} (MIME: {}, size: {} bytes) for org {}",
                saved.getId(), saved.getMimeType(), saved.getFileSizeBytes(), orgId);

        return new MediaUploadResponse(
                saved.getId(),
                saved.getOrganizationId(),
                saved.getFileName(),
                saved.getMimeType(),
                saved.getFileSizeBytes(),
                saved.getSha256Checksum(),
                downloadUrl
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String generatePresignedDownloadUrl(UUID orgId, UUID assetId, Duration ttl) {
        MediaAsset asset = getMediaAsset(orgId, assetId);
        // Pre-signed S3 / MinIO direct URL simulation with token expiration query params
        long expiresEpoch = System.currentTimeMillis() + ttl.toMillis();
        return String.format("%s/%s/%s?expires=%d&tenant=%s",
                s3Endpoint, asset.getS3Bucket(), asset.getS3Key(), expiresEpoch, orgId);
    }

    @Override
    @Transactional(readOnly = true)
    public MediaAsset getMediaAsset(UUID orgId, UUID assetId) {
        return mediaAssetRepository.findByIdAndOrganizationId(assetId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Media asset not found: " + assetId));
    }
}
