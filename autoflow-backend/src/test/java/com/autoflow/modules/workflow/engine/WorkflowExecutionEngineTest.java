package com.autoflow.modules.workflow.engine;

import com.autoflow.modules.ai.service.AiRouterService;
import com.autoflow.modules.channel.provider.instagram.InstagramChannelProvider;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.modules.media.service.AiMediaGeneratorService;
import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import com.autoflow.modules.workflow.entity.Workflow;
import com.autoflow.modules.workflow.entity.WorkflowVersion;
import com.autoflow.modules.workflow.repository.AutomationExecutionRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import com.autoflow.modules.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Workflow Execution Engine DAG Runner Tests")
class WorkflowExecutionEngineTest {

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private WorkflowVersionRepository workflowVersionRepository;

    @Mock
    private AutomationExecutionRepository automationExecutionRepository;

    @Mock
    private WorkflowTriggerEvaluator triggerEvaluator;

    @Mock
    private InstagramChannelProvider instagramChannelProvider;

    @Mock
    private AiRouterService aiRouterService;

    @Mock
    private AiMediaGeneratorService aiMediaGeneratorService;

    @Mock
    private CrmService crmService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WorkflowExecutionEngineImpl executionEngine;

    private final UUID testOrgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        executionEngine = new WorkflowExecutionEngineImpl(
                workflowRepository,
                workflowVersionRepository,
                automationExecutionRepository,
                triggerEvaluator,
                instagramChannelProvider,
                aiRouterService,
                aiMediaGeneratorService,
                crmService,
                objectMapper
        );
    }

    @Test
    @DisplayName("Should execute multi-step DAG actions in topological sequence")
    void shouldExecuteMultiStepDag() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder().name("Lead Magnet").status("PUBLISHED").activeVersionNumber(1).build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        String dagJson = """
                {
                    "nodes": [
                        {"id": "n1", "type": "TRIGGER_INSTAGRAM_COMMENT", "config": {"keywords": ["GUIDE"]}},
                        {"id": "n2", "type": "ACTION_PUBLIC_COMMENT_REPLY", "config": {"reply": "Hi @{{username}}, check your DM!"}},
                        {"id": "n3", "type": "ACTION_SEND_DM", "config": {"message": "Here is the download link, {{username}}!"}},
                        {"id": "n4", "type": "ACTION_AI_REPLY", "config": {"system_prompt": "You are a brand assistant"}},
                        {"id": "n5", "type": "ACTION_SEND_MEDIA", "config": {"asset_type": "PDF", "media_url": "https://s3.autoflow.ai/guide.pdf"}},
                        {"id": "n6", "type": "ACTION_TAG_CONTACT", "config": {"tags": ["lead", "guide_requester"]}}
                    ],
                    "edges": [
                        {"from": "n1", "to": "n2"},
                        {"from": "n2", "to": "n3"},
                        {"from": "n3", "to": "n4"},
                        {"from": "n4", "to": "n5"},
                        {"from": "n5", "to": "n6"}
                    ]
                }
                """;

        WorkflowVersion version = WorkflowVersion.builder().workflow(workflow).versionNumber(1).graphDefinition(dagJson).build();

        InboundEventContext event = InboundEventContext.builder()
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .eventType("COMMENT")
                .externalAccountId("17841405822304914")
                .contactExternalId("ig_user_123")
                .username("john_doe")
                .commentId("comment_999")
                .commentText("I want the GUIDE")
                .pageAccessToken("mock_page_token")
                .build();

        Contact mockContact = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_user_123").build();
        mockContact.setId(UUID.randomUUID());
        mockContact.setOrganizationId(testOrgId);
        Conversation mockConversation = Conversation.builder().organizationId(testOrgId).contact(mockContact).build();

        when(crmService.getOrCreateContact(eq(testOrgId), eq(ChannelType.INSTAGRAM), eq("ig_user_123"), any(), any()))
                .thenReturn(mockContact);
        when(crmService.getOrCreateConversation(eq(testOrgId), eq(mockContact))).thenReturn(mockConversation);

        when(instagramChannelProvider.postPublicCommentReply(eq("mock_page_token"), eq("comment_999"), eq("Hi @john_doe, check your DM!")))
                .thenReturn("reply_111");
        when(instagramChannelProvider.sendPrivateDirectMessage(eq("mock_page_token"), eq("ig_user_123"), eq("Here is the download link, john_doe!")))
                .thenReturn("dm_222");
        when(aiRouterService.generateReply(anyString(), anyString())).thenReturn("AI reply message");
        when(instagramChannelProvider.sendPrivateDirectMessage(eq("mock_page_token"), eq("ig_user_123"), eq("AI reply message")))
                .thenReturn("dm_333");
        when(instagramChannelProvider.sendMediaMessage(eq("mock_page_token"), eq("ig_user_123"), eq("PDF"), eq("https://s3.autoflow.ai/guide.pdf")))
                .thenReturn("media_444");

        when(automationExecutionRepository.save(any(AutomationExecution.class))).thenAnswer(i -> {
            AutomationExecution ex = i.getArgument(0);
            if (ex.getId() == null) ex.setId(UUID.randomUUID());
            return ex;
        });

        // Execute workflow
        AutomationExecution result = executionEngine.executeWorkflow(workflow, version, event);

        // Verify actions executed
        verify(instagramChannelProvider).postPublicCommentReply("mock_page_token", "comment_999", "Hi @john_doe, check your DM!");
        verify(instagramChannelProvider, atLeastOnce()).sendPrivateDirectMessage(eq("mock_page_token"), eq("ig_user_123"), anyString());
        verify(aiRouterService).generateReply("You are a brand assistant", "I want the GUIDE");
        verify(instagramChannelProvider).sendMediaMessage("mock_page_token", "ig_user_123", "PDF", "https://s3.autoflow.ai/guide.pdf");
        verify(crmService).addTagsToContact(eq(testOrgId), eq(mockContact.getId()), eq(List.of("lead", "guide_requester")));

        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNotNull(result.getCompletedAt());
        assertNull(result.getErrorMessage());
    }

    @Test
    @DisplayName("Should successfully re-dispatch and execute retry for failed execution")
    void shouldReDispatchAndExecuteRetrySuccessfully() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder().name("Retry Lead Magnet").status("PUBLISHED").activeVersionNumber(1).build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        String dagJson = """
                {
                    "nodes": [
                        {"id": "n1", "type": "TRIGGER_INSTAGRAM_COMMENT"},
                        {"id": "n2", "type": "ACTION_SEND_DM", "config": {"message": "Retry delivery for {{username}}"}},
                        {"id": "n3", "type": "ACTION_TAG_CONTACT", "config": {"tags": ["retried_lead"]}}
                    ],
                    "edges": [
                        {"from": "n1", "to": "n2"},
                        {"from": "n2", "to": "n3"}
                    ]
                }
                """;

        WorkflowVersion version = WorkflowVersion.builder().workflow(workflow).versionNumber(1).graphDefinition(dagJson).build();

        UUID executionId = UUID.randomUUID();
        AutomationExecution execution = AutomationExecution.builder()
                .id(executionId)
                .organizationId(testOrgId)
                .workflow(workflow)
                .workflowVersion(version)
                .triggerType("TRIGGER_INSTAGRAM_COMMENT")
                .triggerEventId("comment_retry_101")
                .status(ExecutionStatus.RETRYING)
                .retryCount(1)
                .executionContext("{\"username\":\"priya_k\",\"contactExternalId\":\"ig_user_888\"}")
                .build();

        Contact mockContact = Contact.builder().channel(ChannelType.INSTAGRAM).externalId("ig_user_888").build();
        mockContact.setId(UUID.randomUUID());
        mockContact.setOrganizationId(testOrgId);
        Conversation mockConversation = Conversation.builder().organizationId(testOrgId).contact(mockContact).build();

        when(automationExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        when(automationExecutionRepository.save(any(AutomationExecution.class))).thenAnswer(i -> i.getArgument(0));

        when(crmService.getOrCreateContact(eq(testOrgId), eq(ChannelType.INSTAGRAM), eq("ig_user_888"), any(), any()))
                .thenReturn(mockContact);
        when(crmService.getOrCreateConversation(eq(testOrgId), eq(mockContact))).thenReturn(mockConversation);
        when(instagramChannelProvider.sendPrivateDirectMessage(any(), eq("ig_user_888"), eq("Retry delivery for priya_k")))
                .thenReturn("dm_retry_ok");

        CompletableFuture<AutomationExecution> future = executionEngine.reDispatchExecution(executionId);
        AutomationExecution result = future.join();

        assertNotNull(result);
        assertEquals(ExecutionStatus.SUCCESS, result.getStatus());
        assertNull(result.getErrorMessage());
        assertNotNull(result.getCompletedAt());

        // Verify actions executed upon retry
        verify(instagramChannelProvider).sendPrivateDirectMessage(any(), eq("ig_user_888"), eq("Retry delivery for priya_k"));
        verify(crmService).addTagsToContact(eq(testOrgId), eq(mockContact.getId()), eq(List.of("retried_lead")));
    }

    @Test
    @DisplayName("Should handle failure during retry and set terminal status to FAILED")
    void shouldHandleFailureDuringRetryAndMarkFailed() {
        UUID workflowId = UUID.randomUUID();
        Workflow workflow = Workflow.builder().name("Failing Flow").status("PUBLISHED").activeVersionNumber(1).build();
        workflow.setId(workflowId);
        workflow.setOrganizationId(testOrgId);

        String dagJson = """
                {
                    "nodes": [
                        {"id": "n1", "type": "TRIGGER_INSTAGRAM_COMMENT"},
                        {"id": "n2", "type": "ACTION_SEND_DM", "config": {"message": "Hello"}}
                    ],
                    "edges": [
                        {"from": "n1", "to": "n2"}
                    ]
                }
                """;

        WorkflowVersion version = WorkflowVersion.builder().workflow(workflow).versionNumber(1).graphDefinition(dagJson).build();

        UUID executionId = UUID.randomUUID();
        AutomationExecution execution = AutomationExecution.builder()
                .id(executionId)
                .organizationId(testOrgId)
                .workflow(workflow)
                .workflowVersion(version)
                .triggerType("TRIGGER_INSTAGRAM_COMMENT")
                .triggerEventId("comment_fail_202")
                .status(ExecutionStatus.RETRYING)
                .retryCount(1)
                .executionContext("{\"username\":\"alex\",\"contactExternalId\":\"ig_user_999\"}")
                .build();

        when(automationExecutionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        when(automationExecutionRepository.save(any(AutomationExecution.class))).thenAnswer(i -> i.getArgument(0));

        when(instagramChannelProvider.sendPrivateDirectMessage(any(), eq("ig_user_999"), anyString()))
                .thenThrow(new RuntimeException("Meta Graph API error: User blocked messages"));

        CompletableFuture<AutomationExecution> future = executionEngine.reDispatchExecution(executionId);
        AutomationExecution result = future.join();

        assertNotNull(result);
        assertEquals(ExecutionStatus.FAILED, result.getStatus());
        assertEquals("Meta Graph API error: User blocked messages", result.getErrorMessage());
        assertNotNull(result.getCompletedAt());
    }

    @Test
    @DisplayName("Should dynamically generate AI media asset and dispatch media DM with generated URL")
    void shouldExecuteAiMediaGenerationAndDynamicSendMedia() {
        Workflow workflow = Workflow.builder()
                .name("Dynamic AI Media Workflow")
                .status("PUBLISHED")
                .activeVersionNumber(1)
                .build();
        workflow.setId(UUID.randomUUID());
        workflow.setOrganizationId(testOrgId);

        String graphJson = """
        {
          "nodes": [
            {
              "id": "node_trig",
              "type": "TRIGGER_INSTAGRAM_COMMENT",
              "config": { "keywords": ["REWARD"] }
            },
            {
              "id": "node_ai_gen",
              "type": "ACTION_GENERATE_AI_MEDIA",
              "config": {
                "template_type": "COUPON_CARD",
                "prompt": "VIP Reward for {{username}}",
                "headline": "Exclusive for {{username}}",
                "badge_text": "VIP-SAVE-20"
              }
            },
            {
              "id": "node_send_media",
              "type": "ACTION_SEND_MEDIA",
              "config": {
                "asset_type": "IMAGE",
                "media_url": "{{lastGeneratedMediaUrl}}"
              }
            }
          ],
          "edges": [
            { "id": "e1", "from": "node_trig", "to": "node_ai_gen" },
            { "id": "e2", "from": "node_ai_gen", "to": "node_send_media" }
          ]
        }
        """;

        WorkflowVersion version = WorkflowVersion.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .versionNumber(1)
                .graphDefinition(graphJson)
                .build();

        InboundEventContext event = InboundEventContext.builder()
                .organizationId(testOrgId)
                .channel(ChannelType.INSTAGRAM)
                .contactExternalId("ig_contact_456")
                .username("sarah_design")
                .pageAccessToken("eaab_token_123")
                .commentId("comment_777")
                .commentText("Send me the REWARD!")
                .build();

        when(automationExecutionRepository.save(any(AutomationExecution.class))).thenAnswer(i -> i.getArgument(0));

        com.autoflow.modules.media.service.MediaStorageService.MediaUploadResponse mockUpload =
                new com.autoflow.modules.media.service.MediaStorageService.MediaUploadResponse(
                        UUID.randomUUID(),
                        testOrgId,
                        "ai_asset_123.png",
                        "image/png",
                        12345L,
                        "sha256_mock_hash",
                        "https://s3.autoflow.ai/tenants/media/ai_asset_123.png"
                );

        when(aiMediaGeneratorService.generateBrandedAsset(eq(testOrgId), any())).thenReturn(mockUpload);
        when(instagramChannelProvider.sendMediaMessage(eq("eaab_token_123"), eq("ig_contact_456"), eq("IMAGE"), eq("https://s3.autoflow.ai/tenants/media/ai_asset_123.png")))
                .thenReturn("msg_media_outbound_999");

        AutomationExecution execution = executionEngine.executeWorkflow(workflow, version, event);

        assertNotNull(execution);
        assertEquals(ExecutionStatus.SUCCESS, execution.getStatus());
        verify(aiMediaGeneratorService, times(1)).generateBrandedAsset(eq(testOrgId), any());
        verify(instagramChannelProvider, times(1)).sendMediaMessage(
                eq("eaab_token_123"),
                eq("ig_contact_456"),
                eq("IMAGE"),
                eq("https://s3.autoflow.ai/tenants/media/ai_asset_123.png")
        );
    }
}

