package com.autoflow.common.exceptions;

import org.springframework.http.HttpStatus;

public class TenantMismatchException extends AutoFlowException {

    public TenantMismatchException(String message) {
        super("CROSS_TENANT_ACCESS_DENIED", message, HttpStatus.FORBIDDEN);
    }
}
