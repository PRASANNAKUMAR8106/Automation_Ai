package com.autoflow.modules.media.service;

import com.autoflow.modules.media.entity.MediaAsset;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

public interface MediaStorageService {

    record MediaUploadResponse(
            UUID id,
            UUID organizationId,
            String fileName,
            String mimeType,
            long fileSizeBytes,
            String sha256Checksum,
            String downloadUrl
    ) {}

    MediaUploadResponse uploadFile(
            UUID orgId,
            String originalFileName,
            String declaredContentType,
            InputStream inputStream,
            long maxSizeBytes
    );

    String generatePresignedDownloadUrl(UUID orgId, UUID assetId, Duration ttl);

    MediaAsset getMediaAsset(UUID orgId, UUID assetId);
}
