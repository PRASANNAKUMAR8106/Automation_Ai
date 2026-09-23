package com.autoflow.modules.template.service;

import com.autoflow.modules.template.dto.TemplateDto.*;
import com.autoflow.modules.workflow.dto.WorkflowResponse;

import java.util.List;
import java.util.UUID;

public interface TemplateService {
    List<TemplateResponse> listTemplates(String category, String tag, Boolean featured);
    TemplateResponse getTemplateById(UUID id);
    WorkflowResponse instantiateTemplate(UUID orgId, UUID templateId, InstantiateTemplateRequest request);
}
