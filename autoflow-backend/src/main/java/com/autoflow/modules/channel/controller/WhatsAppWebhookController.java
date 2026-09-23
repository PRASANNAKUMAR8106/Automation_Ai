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

import com.autoflow.modules.webhook.service.WebhookReplayHandler;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@RequiredArgsConstructor
@Tag(name = "WhatsApp Webhooks", description = "Meta WhatsApp Cloud API webhook verification and message ingestion endpoint")
public class WhatsAppWebhookController implements WebhookReplayHandler {

    private final ConnectedAccountRepository connectedAccountRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final WorkflowExecutionEngine workflowExecutionEngine;
    private final CrmService crmService;
    private final TokenEncryptionService tokenEncryptionService;
    private final ObjectMapper objectMapper;

    @Value("${autoflow.channels.whatsapp.app-secret:dummy_wa_app_secret}")
    private String whatsAppAppSecret;

    @Value("${autoflow.channels.whatsapp.verify-token:autoflow_webhook_verify_token_2026}")
    private String whatsAppVerifyToken;

    @Override
    public boolean supports(String provider) {
        return "WHATSAPP".equalsIgnoreCase(provider);
    }

    @Override
    public void replay(String payload) {
        processInboundPayload(payload);
    }

    @GetMapping
    @Operation(summary = "WhatsApp Webhook Verification Challenge", description = "Validates the webhook endpoint with Meta Cloud API")
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge
    ) {
        if ("subscribe".equalsIgnoreCase(mode) && whatsAppVerifyToken.equals(verifyToken)) {
            log.info("WhatsApp webhook verification challenge succeeded");
            return ResponseEntity.ok(challenge != null ? challenge : "");
        }
        log.warn("WhatsApp webhook verification challenge failed: mode={}, token={}", mode, verifyToken);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Verification failed");
    }

    @PostMapping
    @Operation(summary = "WhatsApp Inbound Event Delivery", description = "Receives real-time incoming messages from WhatsApp Cloud API")
    public ResponseEntity<String> handleInboundEvent(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature
    ) {
        if (!isSignatureValid(rawPayload, signature)) {
            log.warn("Invalid WhatsApp X-Hub-Signature-256 signature received");
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
            log.error("Failed to parse WhatsApp webhook payload JSON", e);
            throw new IllegalArgumentException("Malformed JSON", e);
        }

        JsonNode entryArray = root.path("entry");
        if (!entryArray.isArray()) {
            return;
        }

        for (JsonNode entry : entryArray) {
            JsonNode changes = entry.path("changes");
            if (!changes.isArray()) continue;

            for (JsonNode change : changes) {
                String field = change.path("field").asText("");
                if (!"messages".equals(field)) continue;

                JsonNode value = change.path("value");
                String phoneNumberId = value.path("metadata").path("phone_number_id").asText();
                JsonNode messages = value.path("messages");
                if (!messages.isArray() || messages.isEmpty()) continue;

                // Extract contact name from contacts array if available
                String senderProfileName = "WhatsApp User";
                JsonNode contactsNode = value.path("contacts");
                if (contactsNode.isArray() && !contactsNode.isEmpty()) {
                    senderProfileName = contactsNode.get(0).path("profile").path("name").asText("WhatsApp User");
                }

                for (JsonNode msgNode : messages) {
                    processIncomingMessage(phoneNumberId, msgNode, senderProfileName);
                }
            }
        }
    }

    private void processIncomingMessage(String phoneNumberId, JsonNode msgNode, String senderProfileName) {
        String messageId = msgNode.path("id").asText();
        String fromPhone = msgNode.path("from").asText();
        String msgType = msgNode.path("type").asText("text");
        String messageText = "";

        if ("text".equalsIgnoreCase(msgType)) {
            messageText = msgNode.path("text").path("body").asText();
        } else if ("button".equalsIgnoreCase(msgType)) {
            messageText = msgNode.path("button").path("text").asText();
        } else if ("interactive".equalsIgnoreCase(msgType)) {
            messageText = msgNode.path("interactive").path("button_reply").path("title").asText();
        }

        // Idempotency check via WebhookEvent table
        String idempotencyKey = "wa_msg_" + messageId;
        if (webhookEventRepository.existsByProviderAndEventId("WHATSAPP", idempotencyKey)) {
            log.info("WhatsApp message {} already processed, skipping", idempotencyKey);
            return;
        }

        // Resolve connected account by external account ID (phoneNumberId)
        Optional<ConnectedAccount> accountOpt = connectedAccountRepository.findByChannelAndExternalAccountId(
                ChannelType.WHATSAPP, phoneNumberId
        );

        UUID orgId = accountOpt.map(ConnectedAccount::getOrganizationId).orElse(null);
        String accessToken = "";

        if (accountOpt.isPresent()) {
            try {
                accessToken = tokenEncryptionService.decrypt(accountOpt.get().getEncryptedAccessToken());
            } catch (Exception e) {
                log.warn("Could not decrypt WhatsApp token, falling back: {}", e.getMessage());
                accessToken = "mock_wa_token";
            }
        } else {
            // Fallback for tests or unlinked demo setups
            orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            accessToken = "mock_wa_token";
        }

        // Record in WebhookEvent ledger
        webhookEventRepository.save(WebhookEvent.builder()
                .provider("WHATSAPP")
                .eventId(idempotencyKey)
                .payload(msgNode.toString())
                .build());

        // Ingest into CRM
        Contact contact = crmService.getOrCreateContact(
                orgId,
                ChannelType.WHATSAPP,
                fromPhone,
                fromPhone,
                senderProfileName
        );

        Conversation conversation = crmService.getOrCreateConversation(orgId, contact);
        crmService.recordMessage(
                orgId,
                conversation,
                "INBOUND",
                "CONTACT",
                "TEXT",
                messageText,
                null,
                messageId
        );

        // Build inbound context and trigger workflow engine
        InboundEventContext context = InboundEventContext.builder()
                .organizationId(orgId)
                .channel(ChannelType.WHATSAPP)
                .eventType("DM")
                .messageText(messageText)
                .contactExternalId(fromPhone)
                .username(fromPhone)
                .fullName(senderProfileName)
                .pageAccessToken(accessToken)
                .pageId(phoneNumberId)
                .build();

        workflowExecutionEngine.evaluateAndExecute(context);
    }

    private boolean isSignatureValid(String payload, String signatureHeader) {
        if (whatsAppAppSecret == null || whatsAppAppSecret.startsWith("dummy") || whatsAppAppSecret.startsWith("test")) {
            return true; // Bypass signature verification in mock/test mode
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }

        try {
            String expectedHash = signatureHeader.substring(7);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(whatsAppAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] calculatedBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = HexFormat.of().formatHex(calculatedBytes);

            return MessageDigest.isEqual(
                    expectedHash.getBytes(StandardCharsets.UTF_8),
                    calculatedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying WhatsApp webhook signature: {}", e.getMessage());
            return false;
        }
    }
}
