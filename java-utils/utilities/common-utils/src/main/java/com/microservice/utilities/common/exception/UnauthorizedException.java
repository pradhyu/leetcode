package com.microservice.utilities.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when authentication or authorization fails.
 */
public class UnauthorizedException extends BusinessException {

    private static final String DEFAULT_ERROR_CODE = "UNAUTHORIZED";

    public UnauthorizedException(String message) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, DEFAULT_ERROR_CODE, HttpStatus.UNAUTHORIZED, cause);
    }

    public UnauthorizedException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, HttpStatus.UNAUTHORIZED, cause);
    }

    // Convenience factory methods
    public static UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or expired authentication token");
    }

    public static UnauthorizedException missingToken() {
        return new UnauthorizedException("Authentication token is required");
    }

    public static UnauthorizedException insufficientPermissions() {
        return new UnauthorizedException("Insufficient permissions to access this resource", "FORBIDDEN");
    }
}