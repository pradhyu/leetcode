package com.microservice.utilities.common.exception;

import com.microservice.utilities.common.dto.ValidationError;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Exception thrown when validation errors occur.
 * Contains detailed validation error information for client consumption.
 */
public class ValidationException extends BusinessException {

    private static final String DEFAULT_ERROR_CODE = "VALIDATION_ERROR";
    private final List<ValidationError> validationErrors;

    public ValidationException(String message) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.BAD_REQUEST);
        this.validationErrors = null;
    }

    public ValidationException(String message, List<ValidationError> validationErrors) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.BAD_REQUEST, validationErrors);
        this.validationErrors = validationErrors;
    }

    public ValidationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.BAD_REQUEST);
        this.validationErrors = null;
    }

    public ValidationException(String message, String errorCode, List<ValidationError> validationErrors) {
        super(message, errorCode, HttpStatus.BAD_REQUEST, validationErrors);
        this.validationErrors = validationErrors;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    public boolean hasValidationErrors() {
        return validationErrors != null && !validationErrors.isEmpty();
    }
}