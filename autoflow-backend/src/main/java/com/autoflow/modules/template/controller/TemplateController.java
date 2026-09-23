package com.autoflow.modules.template.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.template.dto.TemplateDto.*;
import com.autoflow.modules.template.service.TemplateService;
import com.autoflow.modules.workflow.dto.WorkflowResponse;
import com.autoflow.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
@Tag(name = "Workflow Templates Marketplace", description = "Endpoints for discovering starter social automation templates and instantiating them into organization workflows")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    @Operation(summary = "List Templates", description = "Returns available pre-built automation templates with category, tag, and featured filtering")
    public ResponseEntity<ApiResponse<List<TemplateResponse>>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean featured
    ) {
        List<TemplateResponse> templates = templateService.listTemplates(category, tag, featured);
        return ResponseEntity.ok(ApiResponse.ok("Templates retrieved successfully", templates));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Template Details", description = "Retrieves full configuration and graph structure of a starter template")
    public ResponseEntity<ApiResponse<TemplateResponse>> getTemplate(@PathVariable UUID id) {
        TemplateResponse template = templateService.getTemplateById(id);
        return ResponseEntity.ok(ApiResponse.ok("Template retrieved successfully", template));
    }

    @PostMapping("/{id}/instantiate")
    @Operation(summary = "Instantiate Template", description = "Deep-clones a template graph with new node/edge IDs into an organization workflow under strict tenant isolation")
    public ResponseEntity<ApiResponse<WorkflowResponse>> instantiateTemplate(
            @PathVariable UUID id,
            @RequestBody(required = false) InstantiateTemplateRequest request
    ) {
        UUID orgId = TenantContext.getRequiredTenantId();
        WorkflowResponse cloned = templateService.instantiateTemplate(orgId, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Template instantiated into workflow successfully", cloned));
    }
}
