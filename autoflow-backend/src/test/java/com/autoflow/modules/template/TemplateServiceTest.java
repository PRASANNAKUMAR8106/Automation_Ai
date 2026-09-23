package com.autoflow.modules.template;

import com.autoflow.common.exceptions.ResourceNotFoundException;
import com.autoflow.common.exceptions.ValidationException;
import com.autoflow.modules.template.dto.TemplateDto.InstantiateTemplateRequest;
import com.autoflow.modules.template.dto.TemplateDto.TemplateResponse;
import com.autoflow.modules.template.entity.Template;
import com.autoflow.modules.template.repository.TemplateRepository;
import com.autoflow.modules.template.service.TemplateServiceImpl;
import com.autoflow.modules.workflow.dto.CreateWorkflowRequest;
import com.autoflow.modules.workflow.dto.WorkflowResponse;
import com.autoflow.modules.workflow.engine.DagModel;
import com.autoflow.modules.workflow.service.WorkflowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateService Unit Tests")
class TemplateServiceTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private WorkflowService workflowService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TemplateServiceImpl templateService;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        templateService = new TemplateServiceImpl(templateRepository, workflowService, objectMapper);
    }

    @Test
    @DisplayName("listTemplates filters by category, tag, and featured correctly")
    void testListTemplatesFiltering() {
        Template t1 = Template.builder()
                .id(UUID.randomUUID())
                .name("IG Comment to DM")
                .category("LEAD_MAGNET")
                .tags(List.of("instagram", "pdf", "lead"))
                .isFeatured(true)
                .createdAt(Instant.now())
                .build();

        Template t2 = Template.builder()
                .id(UUID.randomUUID())
                .name("Abandoned Cart Recovery")
                .category("ECOMMERCE")
                .tags(List.of("shopify", "cart"))
                .isFeatured(false)
                .createdAt(Instant.now().minusSeconds(100))
                .build();

        Template t3 = Template.builder()
                .id(UUID.randomUUID())
                .name("VIP Webinar Access")
                .category("LEAD_MAGNET")
                .tags(List.of("zoom", "calendar"))
                .isFeatured(false)
                .createdAt(Instant.now().minusSeconds(200))
                .build();

        when(templateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(t1, t2, t3));

        // 1. All templates (no filter)
        List<TemplateResponse> all = templateService.listTemplates(null, null, null);
        assertEquals(3, all.size());

        // 2. Filter by category (case-insensitive)
        List<TemplateResponse> leadMagnets = templateService.listTemplates("lead_magnet", null, null);
        assertEquals(2, leadMagnets.size());
        assertTrue(leadMagnets.stream().allMatch(t -> t.getCategory().equals("LEAD_MAGNET")));

        // 3. Filter by tag (case-insensitive)
        List<TemplateResponse> cartTemplates = templateService.listTemplates(null, "CART", null);
        assertEquals(1, cartTemplates.size());
        assertEquals("Abandoned Cart Recovery", cartTemplates.get(0).getName());

        // 4. Filter by featured
        List<TemplateResponse> featuredOnly = templateService.listTemplates(null, null, true);
        assertEquals(1, featuredOnly.size());
        assertEquals("IG Comment to DM", featuredOnly.get(0).getName());

        // 5. Combined filter
        List<TemplateResponse> combined = templateService.listTemplates("LEAD_MAGNET", "instagram", true);
        assertEquals(1, combined.size());
        assertEquals("IG Comment to DM", combined.get(0).getName());
    }

    @Test
    @DisplayName("getTemplateById returns template or throws ResourceNotFoundException")
    void testGetTemplateById() {
        UUID id = UUID.randomUUID();
        Template template = Template.builder()
                .id(id)
                .name("Flash Sale Alert")
                .category("ECOMMERCE")
                .build();

        when(templateRepository.findById(id)).thenReturn(Optional.of(template));

        TemplateResponse found = templateService.getTemplateById(id);
        assertNotNull(found);
        assertEquals("Flash Sale Alert", found.getName());

        UUID missingId = UUID.randomUUID();
        when(templateRepository.findById(missingId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> templateService.getTemplateById(missingId));
    }

    @Test
    @DisplayName("instantiateTemplate deep-clones DAG with brand new node IDs and remapped edges")
    void testInstantiateTemplateDeepClone() throws Exception {
        UUID templateId = UUID.randomUUID();
        String validDagJson = "{\n" +
                "  \"nodes\": [\n" +
                "    {\"id\": \"t_node_trigger\", \"type\": \"TRIGGER_INSTAGRAM_COMMENT\", \"config\": {\"keyword\": \"GUIDE\"}},\n" +
                "    {\"id\": \"t_node_action\", \"type\": \"ACTION_INSTAGRAM_DM\", \"config\": {\"message\": \"Here is your PDF!\"}}\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    {\"from\": \"t_node_trigger\", \"to\": \"t_node_action\", \"condition\": \"true\"}\n" +
                "  ]\n" +
                "}";

        Template template = Template.builder()
                .id(templateId)
                .name("Default Template Name")
                .description("Default description")
                .category("LEAD_MAGNET")
                .graphDefinition(validDagJson)
                .build();

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        UUID createdWorkflowId = UUID.randomUUID();
        WorkflowResponse mockWorkflowResponse = WorkflowResponse.builder()
                .id(createdWorkflowId)
                .organizationId(testOrgId)
                .name("My Custom Lead Magnet")
                .description("Customized Description")
                .status("DRAFT")
                .activeVersionNumber(1)
                .build();

        when(workflowService.createWorkflow(eq(testOrgId), any(CreateWorkflowRequest.class)))
                .thenReturn(mockWorkflowResponse);

        InstantiateTemplateRequest request = InstantiateTemplateRequest.builder()
                .workflowName("My Custom Lead Magnet")
                .description("Customized Description")
                .build();

        WorkflowResponse result = templateService.instantiateTemplate(testOrgId, templateId, request);

        assertNotNull(result);
        assertEquals(createdWorkflowId, result.getId());
        assertEquals("My Custom Lead Magnet", result.getName());

        // Verify that original template was NOT modified
        assertEquals("Default Template Name", template.getName());
        assertEquals(validDagJson, template.getGraphDefinition());

        // Verify the CreateWorkflowRequest passed to workflowService
        ArgumentCaptor<CreateWorkflowRequest> reqCaptor = ArgumentCaptor.forClass(CreateWorkflowRequest.class);
        verify(workflowService).createWorkflow(eq(testOrgId), reqCaptor.capture());

        CreateWorkflowRequest captured = reqCaptor.getValue();
        assertEquals("My Custom Lead Magnet", captured.getName());
        assertEquals("Customized Description", captured.getDescription());

        // Verify the cloned DAG structure
        DagModel clonedDag = DagModel.fromJson(captured.getInitialGraphDefinition(), objectMapper);
        assertEquals(2, clonedDag.getNodes().size());
        assertEquals(1, clonedDag.getEdges().size());

        // Check that new IDs were generated and are NOT the old IDs
        String newTriggerId = clonedDag.getNodes().get(0).getId();
        String newActionId = clonedDag.getNodes().get(1).getId();
        assertNotEquals("t_node_trigger", newTriggerId);
        assertNotEquals("t_node_action", newActionId);
        assertTrue(newTriggerId.startsWith("node_"));
        assertTrue(newActionId.startsWith("node_"));

        // Check that node configurations were preserved
        assertEquals("GUIDE", clonedDag.getNodes().get(0).getConfig().get("keyword"));
        assertEquals("Here is your PDF!", clonedDag.getNodes().get(1).getConfig().get("message"));

        // Check that edges were properly remapped to point to new IDs
        DagModel.DagEdge edge = clonedDag.getEdges().get(0);
        assertEquals(newTriggerId, edge.getFrom());
        assertEquals(newActionId, edge.getTo());
        assertEquals("true", edge.getCondition());
    }

    @Test
    @DisplayName("instantiateTemplate falls back to template name and description when request is empty")
    void testInstantiateTemplateFallbackName() {
        UUID templateId = UUID.randomUUID();
        String validDagJson = "{\n" +
                "  \"nodes\": [\n" +
                "    {\"id\": \"node-1\", \"type\": \"TRIGGER_INSTAGRAM_COMMENT\"}\n" +
                "  ],\n" +
                "  \"edges\": []\n" +
                "}";

        Template template = Template.builder()
                .id(templateId)
                .name("Standard Coupon Bot")
                .description("Sends promo codes")
                .graphDefinition(validDagJson)
                .build();

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(template));

        when(workflowService.createWorkflow(eq(testOrgId), any(CreateWorkflowRequest.class)))
                .thenReturn(WorkflowResponse.builder().id(UUID.randomUUID()).build());

        templateService.instantiateTemplate(testOrgId, templateId, null);

        ArgumentCaptor<CreateWorkflowRequest> captor = ArgumentCaptor.forClass(CreateWorkflowRequest.class);
        verify(workflowService).createWorkflow(eq(testOrgId), captor.capture());
        assertEquals("Standard Coupon Bot", captor.getValue().getName());
        assertEquals("Sends promo codes", captor.getValue().getDescription());
    }

    @Test
    @DisplayName("instantiateTemplate throws ValidationException for blank graph or cyclic invalid DAG")
    void testInstantiateTemplateInvalidDag() {
        UUID templateId = UUID.randomUUID();

        // 1. Blank graph definition
        Template emptyGraphTemplate = Template.builder()
                .id(templateId)
                .graphDefinition("")
                .build();
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(emptyGraphTemplate));

        assertThrows(ValidationException.class, () ->
                templateService.instantiateTemplate(testOrgId, templateId, null));

        // 2. DAG missing trigger node (must fail validation)
        String noTriggerDag = "{\n" +
                "  \"nodes\": [\n" +
                "    {\"id\": \"n1\", \"type\": \"ACTION_INSTAGRAM_DM\"}\n" +
                "  ],\n" +
                "  \"edges\": []\n" +
                "}";
        Template invalidDagTemplate = Template.builder()
                .id(templateId)
                .graphDefinition(noTriggerDag)
                .build();
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(invalidDagTemplate));

        assertThrows(ValidationException.class, () ->
                templateService.instantiateTemplate(testOrgId, templateId, null));

        // 3. Cyclic DAG
        String cyclicDag = "{\n" +
                "  \"nodes\": [\n" +
                "    {\"id\": \"t1\", \"type\": \"TRIGGER_INSTAGRAM_COMMENT\"},\n" +
                "    {\"id\": \"a1\", \"type\": \"ACTION_INSTAGRAM_DM\"}\n" +
                "  ],\n" +
                "  \"edges\": [\n" +
                "    {\"from\": \"t1\", \"to\": \"a1\"},\n" +
                "    {\"from\": \"a1\", \"to\": \"t1\"}\n" +
                "  ]\n" +
                "}";
        Template cyclicTemplate = Template.builder()
                .id(templateId)
                .graphDefinition(cyclicDag)
                .build();
        when(templateRepository.findById(templateId)).thenReturn(Optional.of(cyclicTemplate));

        assertThrows(ValidationException.class, () ->
                templateService.instantiateTemplate(testOrgId, templateId, null));
    }
}
