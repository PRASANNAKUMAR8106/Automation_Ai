package com.autoflow.modules.media.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.media.dto.AiMediaGenerateRequest;
import com.autoflow.modules.media.service.AiMediaGeneratorService;
import com.autoflow.modules.media.service.MediaStorageService;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/media/ai")
@RequiredArgsConstructor
@Tag(name = "AI Multi-Modal Media Generation", description = "Template-based Java 2D dynamic visual asset synthesis and S3 delivery")
public class AiMediaController {

    private final AiMediaGeneratorService aiMediaGeneratorService;

    @PostMapping("/generate")
    @Operation(summary = "Generate Branded Visual Asset", description = "Synthesizes custom branded PNG graphics using template-based Java 2D graphics engine with dynamic token interpolation and securely stores in S3")
    public ResponseEntity<ApiResponse<MediaStorageService.MediaUploadResponse>> generateAiMedia(
            @Valid @RequestBody AiMediaGenerateRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        log.info("Generating AI media asset for tenant [{}] with template [{}]", orgId, request.getTemplateType());

        MediaStorageService.MediaUploadResponse response = aiMediaGeneratorService.generateBrandedAsset(orgId, request);
        return ResponseEntity.ok(ApiResponse.ok("Branded asset synthesized successfully", response));
    }
}
