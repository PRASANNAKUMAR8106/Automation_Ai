package com.autoflow.modules.media.controller;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.media.entity.MediaAsset;
import com.autoflow.modules.media.service.MediaStorageService;
import com.autoflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = MediaController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Media Controller Multi-Tenant Access & Security Tests")
class MediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaStorageService mediaStorageService;

    private final UUID testOrgId = UUID.randomUUID();
    private final UUID assetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(testOrgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/media/{id}/download-url returns 200 with presigned URL for authorized tenant")
    void shouldReturnDownloadUrlForAuthorizedTenant() throws Exception {
        String presignedUrl = "http://localhost:9000/autoflow-media/tenants/" + testOrgId + "/media/test.png?expires=123456&tenant=" + testOrgId;
        when(mediaStorageService.generatePresignedDownloadUrl(eq(testOrgId), eq(assetId), any(Duration.class)))
                .thenReturn(presignedUrl);

        mockMvc.perform(get("/api/v1/media/{id}/download-url", assetId)
                        .param("ttlMinutes", "45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloadUrl").value(presignedUrl));

        verify(mediaStorageService).generatePresignedDownloadUrl(testOrgId, assetId, Duration.ofMinutes(45));
    }

    @Test
    @DisplayName("GET /api/v1/media/{id}/download-url returns 404 RESOURCE_NOT_FOUND when unauthorized tenant accesses another tenant's asset")
    void shouldRejectUnauthorizedTenantFromGettingDownloadUrl() throws Exception {
        when(mediaStorageService.generatePresignedDownloadUrl(eq(testOrgId), eq(assetId), any(Duration.class)))
                .thenThrow(new ResourceNotFoundException("MediaAsset", assetId));

        mockMvc.perform(get("/api/v1/media/{id}/download-url", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/media/{id}/download-url bounds TTL to maximum 1440 minutes (24h) to enforce short-lived URLs")
    void shouldBoundTtlToMax24Hours() throws Exception {
        String presignedUrl = "http://localhost:9000/autoflow-media/tenants/" + testOrgId + "/media/test.png?expires=123456&tenant=" + testOrgId;
        when(mediaStorageService.generatePresignedDownloadUrl(eq(testOrgId), eq(assetId), any(Duration.class)))
                .thenReturn(presignedUrl);

        // Request an excessive TTL of 100,000 minutes
        mockMvc.perform(get("/api/v1/media/{id}/download-url", assetId)
                        .param("ttlMinutes", "100000"))
                .andExpect(status().isOk());

        // Controller should clamp to 1440 minutes
        verify(mediaStorageService).generatePresignedDownloadUrl(testOrgId, assetId, Duration.ofMinutes(1440));
    }

    @Test
    @DisplayName("GET /api/v1/media/{id} returns 200 with metadata for authorized tenant")
    void shouldReturnMetadataForAuthorizedTenant() throws Exception {
        MediaAsset asset = MediaAsset.builder()
                .fileName("customer_upload.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(15000L)
                .s3Bucket("autoflow-media")
                .s3Key("tenants/" + testOrgId + "/media/customer_upload.pdf")
                .sha256Checksum("abc123sha")
                .build();
        asset.setId(assetId);
        asset.setOrganizationId(testOrgId);

        when(mediaStorageService.getMediaAsset(eq(testOrgId), eq(assetId))).thenReturn(asset);
        when(mediaStorageService.generatePresignedDownloadUrl(eq(testOrgId), eq(assetId), any(Duration.class)))
                .thenReturn("http://localhost:9000/autoflow-media/tenants/" + testOrgId + "/media/customer_upload.pdf?expires=123");

        mockMvc.perform(get("/api/v1/media/{id}", assetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assetId.toString()))
                .andExpect(jsonPath("$.data.organizationId").value(testOrgId.toString()))
                .andExpect(jsonPath("$.data.fileName").value("customer_upload.pdf"))
                .andExpect(jsonPath("$.data.mimeType").value("application/pdf"))
                .andExpect(jsonPath("$.data.fileSizeBytes").value(15000))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/media/{id} returns 404 RESOURCE_NOT_FOUND when unauthorized tenant attempts to inspect another tenant's asset")
    void shouldRejectUnauthorizedTenantFromGettingMetadata() throws Exception {
        when(mediaStorageService.getMediaAsset(eq(testOrgId), eq(assetId)))
                .thenThrow(new ResourceNotFoundException("MediaAsset", assetId));

        mockMvc.perform(get("/api/v1/media/{id}", assetId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/media/upload successfully uploads and validates media file")
    void shouldUploadFileSuccessfully() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        MediaStorageService.MediaUploadResponse response = new MediaStorageService.MediaUploadResponse(
                assetId,
                testOrgId,
                "receipt.png",
                "image/png",
                8L,
                "checksum123",
                "http://localhost:9000/autoflow-media/tenants/" + testOrgId + "/receipt.png?expires=123"
        );

        when(mediaStorageService.uploadFile(eq(testOrgId), eq("receipt.png"), eq("image/png"), any(), anyLong()))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/v1/media/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(assetId.toString()))
                .andExpect(jsonPath("$.data.fileName").value("receipt.png"))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
    }
}
