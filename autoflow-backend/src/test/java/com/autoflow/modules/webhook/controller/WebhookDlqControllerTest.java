package com.autoflow.modules.webhook.controller;

import com.autoflow.config.OpenApiConfig;
import com.autoflow.modules.auth.security.JwtAuthenticationFilter;
import com.autoflow.modules.webhook.entity.WebhookDlqEntry;
import com.autoflow.modules.webhook.service.WebhookDlqService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WebhookDlqController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {JwtAuthenticationFilter.class, OpenApiConfig.class})
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("Webhook DLQ Controller Web MVC Tests")
class WebhookDlqControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookDlqService webhookDlqService;

    @Test
    @DisplayName("GET /api/v1/webhooks/dlq - List dead letter entries")
    void testListDeadLetters() throws Exception {
        WebhookDlqEntry entry = WebhookDlqEntry.builder()
                .id(UUID.randomUUID())
                .provider("INSTAGRAM")
                .eventId("evt_1")
                .payload("{}")
                .status("DEAD_LETTER")
                .attemptCount(1)
                .receivedAt(Instant.now())
                .build();

        when(webhookDlqService.listDeadLetters(any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(entry)));

        mockMvc.perform(get("/api/v1/webhooks/dlq")
                        .param("provider", "INSTAGRAM")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].provider").value("INSTAGRAM"));
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/dlq/{id}/replay - Replay event")
    void testReplayDeadLetter() throws Exception {
        UUID entryId = UUID.randomUUID();
        WebhookDlqEntry replayed = WebhookDlqEntry.builder()
                .id(entryId)
                .provider("INSTAGRAM")
                .status("REPLAYED")
                .attemptCount(2)
                .build();

        when(webhookDlqService.replayDeadLetter(entryId)).thenReturn(replayed);

        mockMvc.perform(post("/api/v1/webhooks/dlq/" + entryId + "/replay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(entryId.toString()))
                .andExpect(jsonPath("$.data.status").value("REPLAYED"))
                .andExpect(jsonPath("$.data.attemptCount").value(2));

        verify(webhookDlqService).replayDeadLetter(entryId);
    }

    @Test
    @DisplayName("POST /api/v1/webhooks/dlq/{id}/discard - Discard event")
    void testDiscardDeadLetter() throws Exception {
        UUID entryId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/webhooks/dlq/" + entryId + "/discard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(webhookDlqService).discardDeadLetter(entryId);
    }
}
