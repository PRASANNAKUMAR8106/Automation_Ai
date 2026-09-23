package com.autoflow.modules.media.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.modules.media.entity.MediaAsset;
import com.autoflow.modules.media.repository.MediaAssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MediaStorageService Tenant Isolation & Presigned URL Security Tests")
class MediaStorageServiceTest {

    @Mock
    private FileSecurityValidator fileSecurityValidator;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @InjectMocks
    private MediaStorageServiceImpl mediaStorageService;

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(mediaStorageService, "bucketMedia", "autoflow-media");
        ReflectionTestUtils.setField(mediaStorageService, "s3Endpoint", "http://localhost:9000");
    }

    @Test
    @DisplayName("Should successfully upload file with isolated S3 prefix and short-lived presigned URL")
    void shouldUploadFileWithIsolatedS3PrefixAndPresignedUrl() throws Exception {
        FileSecurityValidator.ValidatedFileResult validationResult = new FileSecurityValidator.ValidatedFileResult(
                "voucher_card.png",
                "image/png",
                "png",
                1024L,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                new byte[]{ (byte) 0x89, 0x50, 0x4E, 0x47 }
        );

        when(fileSecurityValidator.validate(any(), any(), any(), anyLong())).thenReturn(validationResult);

        MediaAsset savedAsset = MediaAsset.builder()
                .fileName("voucher_card.png")
                .mimeType("image/png")
                .fileSizeBytes(1024L)
                .s3Bucket("autoflow-media")
                .s3Key("tenants/" + tenantA + "/media/2026/09/" + UUID.randomUUID() + "_voucher_card.png")
                .sha256Checksum("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
                .build();
        savedAsset.setId(assetId);
        savedAsset.setOrganizationId(tenantA);

        when(mediaAssetRepository.save(any(MediaAsset.class))).thenReturn(savedAsset);
        when(mediaAssetRepository.findByIdAndOrganizationId(eq(assetId), eq(tenantA))).thenReturn(Optional.of(savedAsset));

        byte[] dummyContent = "dummy PNG content".getBytes(StandardCharsets.UTF_8);
        MediaStorageService.MediaUploadResponse response = mediaStorageService.uploadFile(
                tenantA,
                "voucher_card.png",
                "image/png",
                new ByteArrayInputStream(dummyContent),
                50 * 1024 * 1024
        );

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(assetId);
        assertThat(response.organizationId()).isEqualTo(tenantA);
        assertThat(response.fileName()).isEqualTo("voucher_card.png");
        assertThat(response.mimeType()).isEqualTo("image/png");
        // Verify download URL is a short-lived presigned URL with expires and tenant params
        assertThat(response.downloadUrl()).contains("http://localhost:9000/autoflow-media/tenants/" + tenantA);
        assertThat(response.downloadUrl()).contains("expires=");
        assertThat(response.downloadUrl()).contains("tenant=" + tenantA);
    }

    @Test
    @DisplayName("Should strictly reject unauthorized Tenant B attempting to access Tenant A's media asset")
    void shouldRejectUnauthorizedTenantAccessingAnotherTenantsMedia() {
        // Tenant B queries asset belonging to Tenant A -> Repository returns empty due to organizationId filter
        when(mediaAssetRepository.findByIdAndOrganizationId(eq(assetId), eq(tenantB))).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> mediaStorageService.getMediaAsset(tenantB, assetId)
        );

        assertThat(ex.getMessage()).contains("MediaAsset");
        assertThat(ex.getMessage()).contains(assetId.toString());
        verify(mediaAssetRepository).findByIdAndOrganizationId(assetId, tenantB);
    }

    @Test
    @DisplayName("Should strictly reject unauthorized Tenant B attempting to generate download URL for Tenant A's media")
    void shouldRejectUnauthorizedTenantGeneratingPresignedUrlForAnotherTenantsMedia() {
        // Tenant B requests presigned download URL for Tenant A's asset
        when(mediaAssetRepository.findByIdAndOrganizationId(eq(assetId), eq(tenantB))).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> mediaStorageService.generatePresignedDownloadUrl(tenantB, assetId, Duration.ofMinutes(60))
        );

        verify(mediaAssetRepository).findByIdAndOrganizationId(assetId, tenantB);
    }

    @Test
    @DisplayName("Should allow authorized Tenant A to generate bounded presigned download URL")
    void shouldAllowAuthorizedTenantToGeneratePresignedDownloadUrl() {
        MediaAsset asset = MediaAsset.builder()
                .fileName("private_customer_contract.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(45000L)
                .s3Bucket("autoflow-media")
                .s3Key("tenants/" + tenantA + "/media/2026/09/contract.pdf")
                .sha256Checksum("hash123")
                .build();
        asset.setId(assetId);
        asset.setOrganizationId(tenantA);

        when(mediaAssetRepository.findByIdAndOrganizationId(eq(assetId), eq(tenantA))).thenReturn(Optional.of(asset));

        long beforeTime = System.currentTimeMillis();
        String presignedUrl = mediaStorageService.generatePresignedDownloadUrl(tenantA, assetId, Duration.ofMinutes(30));
        long afterTime = System.currentTimeMillis();

        assertThat(presignedUrl).isNotNull();
        assertThat(presignedUrl).startsWith("http://localhost:9000/autoflow-media/tenants/" + tenantA + "/media/2026/09/contract.pdf");
        assertThat(presignedUrl).contains("expires=");
        assertThat(presignedUrl).contains("tenant=" + tenantA);

        // Verify expiration timestamp is approximately 30 minutes in the future
        String expiresParam = presignedUrl.substring(presignedUrl.indexOf("expires=") + 8, presignedUrl.indexOf("&tenant="));
        long expiresEpoch = Long.parseLong(expiresParam);
        assertThat(expiresEpoch).isGreaterThanOrEqualTo(beforeTime + Duration.ofMinutes(30).toMillis());
        assertThat(expiresEpoch).isLessThanOrEqualTo(afterTime + Duration.ofMinutes(30).toMillis() + 1000);
    }
}
