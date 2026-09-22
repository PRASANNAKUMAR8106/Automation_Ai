package com.autoflow.common.exceptions;

import org.springframework.http.HttpStatus;

public class QuotaExceededException extends AutoFlowException {

    public QuotaExceededException(String featureName, long currentUsage, long maxLimit) {
        super(
                "QUOTA_EXCEEDED",
                String.format("Quota limit exceeded for feature '%s'. Current usage: %d, Allowed limit: %d. Upgrade plan to continue.", featureName, currentUsage, maxLimit),
                HttpStatus.PAYMENT_REQUIRED
        );
    }
}
