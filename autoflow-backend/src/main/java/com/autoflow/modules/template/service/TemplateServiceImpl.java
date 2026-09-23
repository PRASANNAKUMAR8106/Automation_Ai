package com.autoflow.modules.template.service;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.exceptions.ValidationException;
import com.autoflow.modules.template.dto.TemplateDto.*;
import com.autoflow.modules.template.entity.Template;
import com.autoflow.modules.template.repository.TemplateRepository;
import com.autoflow.modules.workflow.dto.CreateWorkflowRequest;
import com.autoflow.modules.workflow.dto.WorkflowResponse;
import com.autoflow.modules.workflow.engine.DagModel;
import com.autoflow.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final TemplateRepository templateRepository;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<TemplateResponse> listTemplates(String category, String tag, Boolean featured) {
        List<Template> all = templateRepository.findAllByOrderByCreatedAtDesc();

        return all.stream()
                .filter(t -> category == null || category.isBlank() || t.getCategory().equalsIgnoreCase(category.trim()))
                .filter(t -> tag == null || tag.isBlank() || (t.getTags() != null && t.getTags().stream().anyMatch(tg -> tg.equalsIgnoreCase(tag.trim()))))
                .filter(t -> featured == null || !featured || t.isFeatured())
                .map(TemplateResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TemplateResponse getTemplateById(UUID id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template", id));
        return TemplateResponse.fromEntity(template);
    }

    @Override
    @Transactional
    public WorkflowResponse instantiateTemplate(UUID orgId, UUID templateId, InstantiateTemplateRequest request) {
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Template", templateId));

        String rawGraph = template.getGraphDefinition();
        if (rawGraph == null || rawGraph.isBlank()) {
            throw new ValidationException("Template contains empty or invalid graph definition");
        }

        // 1. Validate template DAG structure using strict workflow publishing rules
        DagModel sourceDag;
        try {
            sourceDag = DagModel.fromJson(rawGraph, objectMapper);
            sourceDag.validate();
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("Invalid template graph structure: " + e.getMessage());
        }

        // 2. Deep-clone DAG and generate new node/edge identifiers
        Map<String, String> oldToNewNodeIds = new HashMap<>();
        List<DagModel.DagNode> clonedNodes = new ArrayList<>();

        for (DagModel.DagNode oldNode : sourceDag.getNodes()) {
            String newId = "node_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            oldToNewNodeIds.put(oldNode.getId(), newId);

            Map<String, Object> clonedConfig = new HashMap<>();
            if (oldNode.getConfig() != null) {
                clonedConfig.putAll(oldNode.getConfig());
            }

            clonedNodes.add(DagModel.DagNode.builder()
                    .id(newId)
                    .type(oldNode.getType())
                    .config(clonedConfig)
                    .build());
        }

        List<DagModel.DagEdge> clonedEdges = new ArrayList<>();
        if (sourceDag.getEdges() != null) {
            for (DagModel.DagEdge oldEdge : sourceDag.getEdges()) {
                String newFrom = oldToNewNodeIds.get(oldEdge.getFrom());
                String newTo = oldToNewNodeIds.get(oldEdge.getTo());

                clonedEdges.add(DagModel.DagEdge.builder()
                        .from(newFrom)
                        .to(newTo)
                        .condition(oldEdge.getCondition())
                        .build());
            }
        }

        DagModel clonedDag = DagModel.builder()
                .nodes(clonedNodes)
                .edges(clonedEdges)
                .build();

        // 3. Re-validate the newly cloned DAG
        clonedDag.validate();

        String clonedGraphJson;
        try {
            clonedGraphJson = objectMapper.writeValueAsString(clonedDag);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize cloned DAG model", e);
        }

        // 4. Create new workflow bound strictly to organizationId (global template is untouched)
        String workflowName = (request != null && request.getWorkflowName() != null && !request.getWorkflowName().isBlank())
                ? request.getWorkflowName().trim()
                : template.getName();

        String description = (request != null && request.getDescription() != null && !request.getDescription().isBlank())
                ? request.getDescription().trim()
                : template.getDescription();

        CreateWorkflowRequest createRequest = CreateWorkflowRequest.builder()
                .name(workflowName)
                .description(description)
                .initialGraphDefinition(clonedGraphJson)
                .build();

        log.info("Instantiated template {} ('{}') into new workflow with {} cloned nodes for org {}",
                templateId, template.getName(), clonedNodes.size(), orgId);

        return workflowService.createWorkflow(orgId, createRequest);
    }
}
