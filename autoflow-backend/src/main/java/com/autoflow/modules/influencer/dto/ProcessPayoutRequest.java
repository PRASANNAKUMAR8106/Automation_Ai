package com.autoflow.modules.influencer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPayoutRequest {

    @NotNull(message = "Payout ID is required")
    private UUID payoutId;

    private String transactionReference;

    private String adminNotes;
}
