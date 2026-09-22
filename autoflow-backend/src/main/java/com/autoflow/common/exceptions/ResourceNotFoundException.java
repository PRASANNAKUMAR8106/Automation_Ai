package com.autoflow.common.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends AutoFlowException {

    public ResourceNotFoundException(String resourceName, Object identifier) {
        super("RESOURCE_NOT_FOUND", String.format("%s with identifier '%s' was not found", resourceName, identifier), HttpStatus.NOT_FOUND);
    }
}
