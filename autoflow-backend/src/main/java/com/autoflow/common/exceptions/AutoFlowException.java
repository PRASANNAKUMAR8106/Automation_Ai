package com.autoflow.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for all domain business errors in AutoFlow.
 */
@Getter
public class AutoFlowException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    public AutoFlowException(String errorCode, String message, HttpStatus status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public AutoFlowException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }
}
