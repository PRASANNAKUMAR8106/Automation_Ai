package com.autoflow.common.exceptions;

import com.autoflow.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * Centralized exception translator producing uniform ApiResponse structures.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AutoFlowException.class)
    public ResponseEntity<ApiResponse<Void>> handleAutoFlowException(AutoFlowException ex) {
        log.warn("Business domain exception [code={}]: {}", ex.getErrorCode(), ex.getMessage());
        List<String> details = (ex instanceof ValidationException ve) ? ve.getFieldErrors() : null;
        ApiResponse<Void> body = (details != null && !details.isEmpty())
                ? ApiResponse.error(ex.getErrorCode(), ex.getMessage(), details)
                : ApiResponse.error(ex.getErrorCode(), ex.getMessage());
        ensureCorrelationId(body);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        log.warn("Payload validation failed: {}", errors);
        ApiResponse<Void> body = ApiResponse.error("VALIDATION_FAILED", "Request body contains invalid arguments", errors);
        ensureCorrelationId(body);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        log.warn("Constraint violation: {}", errors);
        ApiResponse<Void> body = ApiResponse.error("CONSTRAINT_VIOLATION", "Constraint validation failed", errors);
        ensureCorrelationId(body);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiResponse<Void> body = ApiResponse.error("FORBIDDEN", "You do not have permission to execute this operation");
        ensureCorrelationId(body);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String correlationId = MDC.get("correlationId");
        log.error("Unhandled internal server error [correlationId={}]:", correlationId, ex);
        ApiResponse<Void> body = ApiResponse.error("INTERNAL_SERVER_ERROR", "An unexpected server error occurred. Please contact support.");
        ensureCorrelationId(body);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private void ensureCorrelationId(ApiResponse<Void> body) {
        if (body.getCorrelationId() == null || body.getCorrelationId().isBlank()) {
            String cid = MDC.get("correlationId");
            body.setCorrelationId(cid != null ? cid : UUID.randomUUID().toString());
        }
    }
}
