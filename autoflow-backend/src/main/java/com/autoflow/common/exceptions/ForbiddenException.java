package com.autoflow.common.exceptions;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends AutoFlowException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
