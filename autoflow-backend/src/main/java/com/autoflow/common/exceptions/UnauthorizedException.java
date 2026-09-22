package com.autoflow.common.exceptions;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends AutoFlowException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }
}
