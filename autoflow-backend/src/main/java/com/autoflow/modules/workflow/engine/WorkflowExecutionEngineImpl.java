package com.autoflow.modules.workflow.engine;

import com.autoflow.modules.ai.service.AiRouterService;
import com.autoflow.modules.channel.provider.instagram.InstagramChannelProvider;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.modules.workflow.entity.AutomationExecution;
import com.autoflow.modules.workflow.entity.ExecutionStatus;
import com.autoflow.modules.workflow.entity.Workflow;
import com.autoflow.modules.workflow.entity.WorkflowVersion;
import com.autoflow.modules.workflow.repository.AutomationExecutionRepository;
import com.autoflow.modules.workflow.repository.WorkflowRepository;
import com.autoflow.modules.workflow.repository.WorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionEngineImpl implements WorkflowExecutionEngine {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final AutomationExecutionRepository automationExecutionRepository;
    private final WorkflowTriggerEvaluator triggerEvaluator;
    private final InstagramChannelProvider instagramChannelProvider;
    private final AiRouterService aiRouterService;
    private final CrmService crmService;
    private final ObjectMapper objectMapper;

    // Concurrently execute workflows on lightweight Virtual Threads
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void evaluateAndExecute(InboundEventContext event) {
        if (event == null || event.getOrganizationId() == null) {
            return;
        }

        List<Workflow> publishedWorkflows = workflowRepository.findByOrganizationId(event.getOrganizationId())
                .stream()
                .filter(w -> "PUBLISHED".equalsIgnoreCase(w.getStatus()))
                .toList();

        for (Workflow workflow : publishedWorkflows) {
            if (workflow.getActiveVersionNumber() == null) continue;

            Optional<WorkflowVersion> versionOpt = workflowVersionRepository.findByWorkflowIdAndVersionNumber(
                    workflow.getId(),
                    workflow.getActiveVersionNumber()
            );

            if (versionOpt.isEmpty()) continue;
            WorkflowVersion version = versionOpt.get();

            try {
                DagModel dag = DagModel.fromJson(version.getGraphDefinition(), objectMapper);
                Optional<DagModel.DagNode> triggerNode = dag.findTriggerNode();

                if (triggerNode.isPresent() && triggerEvaluator.matches(triggerNode.get(), event)) {
                    log.info("Trigger matched for workflow {} ('{}') on event {}",
                            workflow.getId(), workflow.getName(), event.getEventType());

                    // Dispatch execution asynchronously on Virtual Thread
                    virtualExecutor.submit(() -> {
                        try {
                            executeWorkflow(workflow, version, event);
                        } catch (Exception e) {
                            log.error("Error running workflow {}: {}", workflow.getId(), e.getMessage(), e);
                        }
                    });
                }
            } catch (Exception e) {
                log.warn("Failed to evaluate trigger for workflow {}: {}", workflow.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public AutomationExecution executeWorkflow(Workflow workflow, WorkflowVersion version, InboundEventContext event) {
        DagModel dag = DagModel.fromJson(version.getGraphDefinition(), objectMapper);
        DagModel.DagNode triggerNode = dag.findTriggerNode().orElse(null);

        AutomationExecution execution = AutomationExecution.builder()
                .organizationId(event.getOrganizationId())
                .workflow(workflow)
                .workflowVersion(version)
                .triggerType(triggerNode != null ? triggerNode.getType() : "UNKNOWN")
                .triggerEventId(event.getCommentId() != null ? event.getCommentId() : event.getMessageId())
                .status(ExecutionStatus.RUNNING)
                .startedAt(Instant.now())
                .build();
        execution = automationExecutionRepository.save(execution);

        Map<String, Object> runContext = new HashMap<>();
        runContext.put("username", event.getUsername() != null ? event.getUsername() : "friend");
        runContext.put("fullName", event.getFullName() != null ? event.getFullName() : "");
        runContext.put("commentText", event.getCommentText() != null ? event.getCommentText() : "");
        runContext.put("messageText", event.getMessageText() != null ? event.getMessageText() : "");

        try {
            List<DagModel.DagNode> nodes = dag.getTopologicalOrder();

            for (DagModel.DagNode node : nodes) {
                execution.setCurrentNodeId(node.getId());
                executeNode(node, event, runContext);
            }

            execution.setStatus(ExecutionStatus.SUCCESS);
            execution.setCompletedAt(Instant.now());
            execution.setExecutionContext(objectMapper.writeValueAsString(runContext));
            log.info("Successfully executed workflow {} for org {}", workflow.getId(), event.getOrganizationId());
        } catch (Exception e) {
            log.error("Workflow execution failed at node {}: {}", execution.getCurrentNodeId(), e.getMessage(), e);
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setErrorMessage(e.getMessage());
            execution.setCompletedAt(Instant.now());
        }

        return automationExecutionRepository.save(execution);
    }

    private void executeNode(DagModel.DagNode node, InboundEventContext event, Map<String, Object> runContext) {
        String type = node.getType();
        if (type == null || type.startsWith("TRIGGER_")) {
            return; // Trigger evaluated prior to execution
        }

        Map<String, Object> config = node.getConfig() != null ? node.getConfig() : Map.of();

        switch (type) {
            case "ACTION_PUBLIC_COMMENT_REPLY" -> {
                String replyTemplate = (String) config.getOrDefault("reply", "Thanks for reaching out!");
                String formattedReply = replacePlaceholders(replyTemplate, runContext);

                if (event.getCommentId() != null) {
                    String replyId = instagramChannelProvider.postPublicCommentReply(
                            event.getPageAccessToken(),
                            event.getCommentId(),
                            formattedReply
                    );
                    runContext.put("lastReplyId", replyId);
                    log.info("Posted public comment reply {}", replyId);
                }
            }

            case "ACTION_SEND_DM" -> {
                String msgTemplate = (String) config.getOrDefault("message", "Hello!");
                String formattedMsg = replacePlaceholders(msgTemplate, runContext);

                if (event.getContactExternalId() != null) {
                    String msgId = instagramChannelProvider.sendPrivateDirectMessage(
                            event.getPageAccessToken(),
                            event.getContactExternalId(),
                            formattedMsg
                    );
                    runContext.put("lastDmId", msgId);

                    // Record outbound message in CRM
                    recordCrmOutbound(event, formattedMsg, null, msgId);
                }
            }

            case "ACTION_AI_REPLY" -> {
                String systemPrompt = (String) config.getOrDefault(
                        "system_prompt",
                        "You are an AI brand assistant. Respond warmly, concisely, and helpfully."
                );
                String userPrompt = event.getCommentText() != null ? event.getCommentText() : event.getMessageText();
                if (userPrompt == null || userPrompt.isBlank()) {
                    userPrompt = "Hello!";
                }

                String aiGeneratedReply = aiRouterService.generateReply(systemPrompt, userPrompt);
                runContext.put("aiReply", aiGeneratedReply);

                if (event.getContactExternalId() != null) {
                    String msgId = instagramChannelProvider.sendPrivateDirectMessage(
                            event.getPageAccessToken(),
                            event.getContactExternalId(),
                            aiGeneratedReply
                    );
                    runContext.put("lastAiDmId", msgId);
                    recordCrmOutbound(event, aiGeneratedReply, null, msgId);
                }
            }

            case "ACTION_SEND_MEDIA" -> {
                String mediaType = (String) config.getOrDefault("asset_type", "IMAGE");
                String mediaUrl = (String) config.getOrDefault("media_url", "https://autoflow.ai/sample.png");

                if (event.getContactExternalId() != null) {
                    String msgId = instagramChannelProvider.sendMediaMessage(
                            event.getPageAccessToken(),
                            event.getContactExternalId(),
                            mediaType,
                            mediaUrl
                    );
                    runContext.put("lastMediaDmId", msgId);
                    recordCrmOutbound(event, "[Media: " + mediaType + "]", mediaUrl, msgId);
                }
            }

            case "ACTION_TAG_CONTACT" -> {
                Object tagsObj = config.get("tags");
                if (tagsObj instanceof Collection<?> tagsList && event.getContactExternalId() != null) {
                    Contact contact = crmService.getOrCreateContact(
                            event.getOrganizationId(),
                            event.getChannel(),
                            event.getContactExternalId(),
                            event.getUsername(),
                            event.getFullName()
                    );
                    List<String> tags = tagsList.stream().map(Object::toString).toList();
                    crmService.addTagsToContact(event.getOrganizationId(), contact.getId(), tags);
                }
            }

            case "ACTION_DELAY" ->
                log.info("Executing simulated delay node {}", node.getId());

            default ->
                log.warn("Unrecognized workflow node type: {}", type);
        }
    }

    private void recordCrmOutbound(InboundEventContext event, String content, String mediaUrl, String externalMessageId) {
        try {
            Contact contact = crmService.getOrCreateContact(
                    event.getOrganizationId(),
                    event.getChannel(),
                    event.getContactExternalId(),
                    event.getUsername(),
                    event.getFullName()
            );
            Conversation conversation = crmService.getOrCreateConversation(event.getOrganizationId(), contact);
            crmService.recordMessage(
                    event.getOrganizationId(),
                    conversation,
                    "OUTBOUND",
                    "BOT",
                    mediaUrl != null ? "MEDIA" : "TEXT",
                    content,
                    mediaUrl,
                    externalMessageId
            );
        } catch (Exception e) {
            log.warn("Failed to record CRM outbound message: {}", e.getMessage());
        }
    }

    private String replacePlaceholders(String template, Map<String, Object> context) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }
}
