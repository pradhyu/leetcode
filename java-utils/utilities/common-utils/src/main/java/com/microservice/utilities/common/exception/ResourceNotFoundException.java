package com.microservice.utilities.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends BusinessException {

    private static final String DEFAULT_ERROR_CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String message) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.NOT_FOUND);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.NOT_FOUND, cause);
    }

    public ResourceNotFoundException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.NOT_FOUND, cause);
    }

    // Convenience factory methods
    public static ResourceNotFoundException forId(String resourceType, Object id) {
        return new ResourceNotFoundException(
                String.format("%s with id '%s' not found", resourceType, id)
        );
    }

    public static ResourceNotFoundException forField(String resourceType, String field, Object value) {
        return new ResourceNotFoundException(
                String.format("%s with %s '%s' not found", resourceType, field, value)
        );
    }
}