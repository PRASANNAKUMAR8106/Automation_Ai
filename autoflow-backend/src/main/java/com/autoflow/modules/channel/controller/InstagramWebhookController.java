package com.autoflow.modules.channel.controller;

import com.autoflow.common.security.TokenEncryptionService;
import com.autoflow.modules.channel.entity.ConnectedAccount;
import com.autoflow.modules.channel.repository.ConnectedAccountRepository;
import com.autoflow.modules.crm.entity.ChannelType;
import com.autoflow.modules.crm.entity.Contact;
import com.autoflow.modules.crm.entity.Conversation;
import com.autoflow.modules.crm.service.CrmService;
import com.autoflow.modules.webhook.entity.WebhookEvent;
import com.autoflow.modules.webhook.repository.WebhookEventRepository;
import com.autoflow.modules.workflow.engine.InboundEventContext;
import com.autoflow.modules.workflow.engine.WorkflowExecutionEngine;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.autoflow.modules.webhook.service.WebhookReplayHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/instagram")
@RequiredArgsConstructor
@Tag(name = "Instagram Webhooks", description = "Meta webhook verification and real-time ingestion endpoint for comments and messages")
public class InstagramWebhookController implements WebhookReplayHandler {

    private final ConnectedAccountRepository connectedAccountRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WorkflowExecutionEngine workflowExecutionEngine;
    private final CrmService crmService;
    private final TokenEncryptionService tokenEncryptionService;
    private final ObjectMapper objectMapper;

    @Value("${autoflow.channels.meta.app-secret:dummy_meta_app_secret}")
    private String metaAppSecret;

    @Value("${autoflow.channels.meta.verify-token:autoflow_webhook_verify_token_2026}")
    private String metaVerifyToken;

    @Override
    public boolean supports(String provider) {
        return "INSTAGRAM".equalsIgnoreCase(provider);
    }

    @Override
    public void replay(String payload) {
        processInboundPayload(payload);
    }

    @GetMapping
    @Operation(summary = "Meta Webhook Verification Challenge", description = "Validates the webhook endpoint with Meta Graph API")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equalsIgnoreCase(mode) && metaVerifyToken.equals(verifyToken)) {
            log.info("Meta webhook verification challenge succeeded");
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        log.warn("Meta webhook verification challenge failed: mode={}, token={}", mode, verifyToken);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    @Operation(summary = "Meta Inbound Event Delivery", description = "Receives real-time comment and DM events from Instagram Graph API")
    public ResponseEntity<String> handleInboundEvent(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) {
        if (!isSignatureValid(rawPayload, signature)) {
            log.warn("Invalid Meta X-Hub-Signature-256 signature received");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }

        try {
            processInboundPayload(rawPayload);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Malformed JSON");
        }

        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    public void processInboundPayload(String rawPayload) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("Failed to parse Meta webhook payload JSON", e);
            throw new IllegalArgumentException("Malformed JSON", e);
        }

        JsonNode entryArray = root.path("entry");
        if (!entryArray.isArray()) {
            return;
        }

        for (JsonNode entry : entryArray) {
            String igAccountId = entry.path("id").asText();

            // Resolve connected account by external account id
            Optional<ConnectedAccount> accountOpt = connectedAccountRepository.findByChannelAndExternalAccountId(
                    ChannelType.INSTAGRAM,
                    igAccountId
            );

            if (accountOpt.isEmpty()) {
                log.debug("No active connected account found for IG id {}", igAccountId);
                continue;
            }

            ConnectedAccount account = accountOpt.get();
            String decryptedToken = decryptTokenSafe(account.getEncryptedAccessToken());

            // 1. Process Comments
            JsonNode changes = entry.path("changes");
            if (changes.isArray()) {
                for (JsonNode change : changes) {
                    if ("comments".equalsIgnoreCase(change.path("field").asText())) {
                        processCommentChange(change.path("value"), account, decryptedToken, rawPayload);
                    }
                }
            }

            // 2. Process Direct Messages
            JsonNode messaging = entry.path("messaging");
            if (messaging.isArray()) {
                for (JsonNode msgNode : messaging) {
                    processMessagingEvent(msgNode, account, decryptedToken, rawPayload);
                }
            }
        }
    }

    private void processCommentChange(JsonNode valueNode, ConnectedAccount account, String pageAccessToken, String rawPayload) {
        String commentId = valueNode.path("id").asText();
        String commentText = valueNode.path("text").asText();
        JsonNode fromNode = valueNode.path("from");
        String fromId = fromNode.path("id").asText();
        String username = fromNode.path("username").asText();

        // Idempotency check
        String eventKey = "comment_" + commentId;
        if (webhookEventRepository.existsByProviderAndEventId("INSTAGRAM", eventKey)) {
            log.info("Duplicate comment webhook {} skipped", eventKey);
            return;
        }
        webhookEventRepository.save(WebhookEvent.builder()
                .provider("INSTAGRAM")
                .eventId(eventKey)
                .payload(rawPayload)
                .build());

        // Ingest into CRM
        Contact contact = crmService.getOrCreateContact(
                account.getOrganizationId(),
                ChannelType.INSTAGRAM,
                fromId,
                username,
                null
        );
        Conversation conversation = crmService.getOrCreateConversation(account.getOrganizationId(), contact);
        crmService.recordMessage(
                account.getOrganizationId(),
                conversation,
                "INBOUND",
                "CONTACT",
                "TEXT",
                commentText,
                null,
                commentId
        );

        // Build execution context and dispatch to workflow engine
        InboundEventContext eventContext = InboundEventContext.builder()
                .organizationId(account.getOrganizationId())
                .channel(ChannelType.INSTAGRAM)
                .eventType("COMMENT")
                .externalAccountId(account.getExternalAccountId())
                .contactExternalId(fromId)
                .username(username)
                .commentId(commentId)
                .commentText(commentText)
                .pageAccessToken(pageAccessToken)
                .build();

        workflowExecutionEngine.evaluateAndExecute(eventContext);
    }

    private void processMessagingEvent(JsonNode msgNode, ConnectedAccount account, String pageAccessToken, String rawPayload) {
        JsonNode message = msgNode.path("message");
        String messageId = message.path("mid").asText();
        String messageText = message.path("text").asText();
        String senderId = msgNode.path("sender").path("id").asText();

        // Idempotency check
        String eventKey = "dm_" + messageId;
        if (webhookEventRepository.existsByProviderAndEventId("INSTAGRAM", eventKey)) {
            log.info("Duplicate DM webhook {} skipped", eventKey);
            return;
        }
        webhookEventRepository.save(WebhookEvent.builder()
                .provider("INSTAGRAM")
                .eventId(eventKey)
                .payload(rawPayload)
                .build());

        // Ingest into CRM
        Contact contact = crmService.getOrCreateContact(
                account.getOrganizationId(),
                ChannelType.INSTAGRAM,
                senderId,
                null,
                null
        );
        Conversation conversation = crmService.getOrCreateConversation(account.getOrganizationId(), contact);
        crmService.recordMessage(
                account.getOrganizationId(),
                conversation,
                "INBOUND",
                "CONTACT",
                "TEXT",
                messageText,
                null,
                messageId
        );

        // Build execution context and dispatch to workflow engine
        InboundEventContext eventContext = InboundEventContext.builder()
                .organizationId(account.getOrganizationId())
                .channel(ChannelType.INSTAGRAM)
                .eventType("DM")
                .externalAccountId(account.getExternalAccountId())
                .contactExternalId(senderId)
                .messageId(messageId)
                .messageText(messageText)
                .pageAccessToken(pageAccessToken)
                .build();

        workflowExecutionEngine.evaluateAndExecute(eventContext);
    }

    private boolean isSignatureValid(String payload, String signatureHeader) {
        if (metaAppSecret == null || metaAppSecret.startsWith("dummy") || metaAppSecret.startsWith("test")) {
            return true; // Mock / local dev mode allows bypass
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        String expectedHash = signatureHeader.substring(7);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(metaAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hashBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = HexFormat.of().formatHex(hashBytes);

            return MessageDigest.isEqual(
                    calculatedHash.getBytes(StandardCharsets.UTF_8),
                    expectedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private String decryptTokenSafe(String encrypted) {
        if (encrypted == null) return null;
        try {
            return tokenEncryptionService.decrypt(encrypted);
        } catch (Exception e) {
            log.warn("Failed to decrypt access token, using raw/mock token: {}", e.getMessage());
            return encrypted;
        }
    }
}
