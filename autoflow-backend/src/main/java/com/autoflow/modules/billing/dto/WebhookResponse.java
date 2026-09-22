package com.autoflow.modules.billing.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private String status;
    private String eventId;
    private String message;
}
