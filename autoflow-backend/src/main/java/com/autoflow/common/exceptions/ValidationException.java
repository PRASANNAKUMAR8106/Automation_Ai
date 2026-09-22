package com.autoflow.common.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
public class ValidationException extends AutoFlowException {

    private final List<String> fieldErrors;

    public ValidationException(String message, List<String> fieldErrors) {
        super("VALIDATION_FAILED", message, HttpStatus.BAD_REQUEST);
        this.fieldErrors = fieldErrors;
    }
}
