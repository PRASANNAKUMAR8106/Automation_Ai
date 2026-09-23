package com.autoflow.modules.media.service;

import com.autoflow.modules.media.dto.AiMediaGenerateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI Media Generator Service Tests")
class AiMediaGeneratorServiceTest {

    @Mock
    private MediaStorageService mediaStorageService;

    private AiMediaGeneratorServiceImpl generatorService;
    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        generatorService = new AiMediaGeneratorServiceImpl(mediaStorageService);
    }

    @Test
    @DisplayName("Should generate high-resolution PNG image and pass to MediaStorageService")
    void shouldGenerateHighResPngImage() throws Exception {
        AiMediaGenerateRequest request = AiMediaGenerateRequest.builder()
                .templateType("COUPON_CARD")
                .prompt("Holiday Super Sale")
                .headline("Save 50% Off Everything")
                .subtext("Use your unique VIP code below")
                .badgeText("LIMITED OFFER")
                .accentColor("#EC4899")
                .width(1080)
                .height(1080)
                .dynamicTokens(Map.of("code", "SAVE50NOW"))
                .build();

        UUID assetId = UUID.randomUUID();
        MediaStorageService.MediaUploadResponse mockUpload = new MediaStorageService.MediaUploadResponse(
                assetId,
                testOrgId,
                "ai_asset_123.png",
                "image/png",
                54321L,
                "sha256_mock_hash",
                "https://s3.autoflow.ai/tenants/media/ai_asset_123.png"
        );

        when(mediaStorageService.uploadFile(eq(testOrgId), anyString(), eq("image/png"), any(InputStream.class), anyLong()))
                .thenReturn(mockUpload);

        MediaStorageService.MediaUploadResponse response = generatorService.generateBrandedAsset(testOrgId, request);

        assertNotNull(response);
        assertEquals(assetId, response.id());
        assertEquals("image/png", response.mimeType());

        ArgumentCaptor<InputStream> streamCaptor = ArgumentCaptor.forClass(InputStream.class);
        verify(mediaStorageService, times(1)).uploadFile(
                eq(testOrgId),
                contains(".png"),
                eq("image/png"),
                streamCaptor.capture(),
                anyLong()
        );

        // Verify PNG magic bytes: 0x89 0x50 0x4E 0x47 0x0D 0x0A 0x1A 0x0A
        InputStream is = streamCaptor.getValue();
        byte[] header = new byte[8];
        int read = is.read(header);
        assertEquals(8, read);
        assertEquals((byte) 0x89, header[0]);
        assertEquals((byte) 0x50, header[1]); // P
        assertEquals((byte) 0x4E, header[2]); // N
        assertEquals((byte) 0x47, header[3]); // G
        assertEquals((byte) 0x0D, header[4]); // \r
        assertEquals((byte) 0x0A, header[5]); // \n
        assertEquals((byte) 0x1A, header[6]);
        assertEquals((byte) 0x0A, header[7]);
    }

    @Test
    @DisplayName("Should handle missing optional fields and fallback gracefully")
    void shouldHandleDefaultFieldsGracefully() {
        AiMediaGenerateRequest request = AiMediaGenerateRequest.builder()
                .prompt("Simple Promo")
                .build();

        MediaStorageService.MediaUploadResponse mockUpload = new MediaStorageService.MediaUploadResponse(
                UUID.randomUUID(),
                testOrgId,
                "ai_asset_default.png",
                "image/png",
                12000L,
                "sha256_mock_hash",
                "https://s3.autoflow.ai/tenants/media/ai_asset_default.png"
        );

        when(mediaStorageService.uploadFile(eq(testOrgId), anyString(), eq("image/png"), any(InputStream.class), anyLong()))
                .thenReturn(mockUpload);

        MediaStorageService.MediaUploadResponse response = generatorService.generateBrandedAsset(testOrgId, request);

        assertNotNull(response);
        verify(mediaStorageService, times(1)).uploadFile(eq(testOrgId), anyString(), eq("image/png"), any(), anyLong());
    }
}
