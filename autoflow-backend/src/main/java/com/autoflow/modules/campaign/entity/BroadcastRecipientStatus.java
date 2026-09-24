package com.autoflow.modules.campaign.entity;

public enum BroadcastRecipientStatus {
    PENDING,
    PROCESSING,
    SENT,
    DELIVERED,
    FAILED,
    SKIPPED_WINDOW,
    SKIPPED_POLICY,
    SKIPPED_OPT_OUT,
    CANCELLED
}
