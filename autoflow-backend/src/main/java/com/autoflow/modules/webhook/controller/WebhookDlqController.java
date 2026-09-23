package com.autoflow.modules.webhook.controller;

import com.autoflow.common.ApiResponse;
import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import com.autoflow.modules.webhook.service.WebhookDlqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/dlq")
@RequiredArgsConstructor
@Tag(name = "Webhook Dead-Letter Queue", description = "Diagnostic inspection and event replay for failed inbound webhooks")
public class WebhookDlqController {

    private final WebhookDlqService webhookDlqService;

    @GetMapping
    @Operation(summary = "List Webhook DLQ Entries", description = "Queries failed webhook deliveries with optional provider and status filters")
    public ResponseEntity<ApiResponse<Page<WebhookDlqEntry>>> listDeadLetters(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<WebhookDlqEntry> entries = webhookDlqService.listDeadLetters(provider, status, page, size);
        return ResponseEntity.ok(ApiResponse.ok("DLQ entries retrieved successfully", entries));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "Replay Dead-Letter Webhook Event", description = "Re-dispatches the stored raw payload to the appropriate channel processor")
    public ResponseEntity<ApiResponse<WebhookDlqEntry>> replayDeadLetter(@PathVariable UUID id) {
        log.info("Replay requested for webhook DLQ entry {}", id);
        WebhookDlqEntry replayed = webhookDlqService.replayDeadLetter(id);
        return ResponseEntity.ok(ApiResponse.ok("Webhook event replayed successfully", replayed));
    }

    @PostMapping("/{id}/discard")
    @Operation(summary = "Discard Dead-Letter Webhook Event", description = "Marks a dead-letter webhook event as discarded without replaying")
    public ResponseEntity<ApiResponse<Void>> discardDeadLetter(@PathVariable UUID id) {
        log.info("Discard requested for webhook DLQ entry {}", id);
        webhookDlqService.discardDeadLetter(id);
        return ResponseEntity.ok(ApiResponse.ok("Webhook event discarded successfully", null));
    }
}
