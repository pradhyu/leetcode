package com.microservice.utilities.rest.validation;

import com.microservice.utilities.common.dto.ValidationError;
import com.microservice.utilities.common.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Validation handler for programmatic validation.
 * Provides utilities for validating objects and converting violations to ValidationErrors.
 */
@Component
public class ValidationHandler {

    private final Validator validator;

    public ValidationHandler(Validator validator) {
        this.validator = validator;
    }

    /**
     * Validate an object and throw ValidationException if violations exist
     */
    public <T> void validateAndThrow(T object) {
        List<ValidationError> errors = validate(object);
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    /**
     * Validate an object and return list of ValidationErrors
     */
    public <T> List<ValidationError> validate(T object) {
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        return convertViolationsToErrors(violations);
    }

    /**
     * Validate specific property of an object
     */
    public <T> List<ValidationError> validateProperty(T object, String propertyName) {
        Set<ConstraintViolation<T>> violations = validator.validateProperty(object, propertyName);
        return convertViolationsToErrors(violations);
    }

    /**
     * Validate property value against object constraints
     */
    public <T> List<ValidationError> validateValue(Class<T> beanType, String propertyName, Object value) {
        Set<ConstraintViolation<T>> violations = validator.validateValue(beanType, propertyName, value);
        return convertViolationsToErrors(violations);
    }

    /**
     * Check if object is valid
     */
    public <T> boolean isValid(T object) {
        return validator.validate(object).isEmpty();
    }

    /**
     * Convert constraint violations to ValidationErrors
     */
    private <T> List<ValidationError> convertViolationsToErrors(Set<ConstraintViolation<T>> violations) {
        List<ValidationError> errors = new ArrayList<>();
        
        for (ConstraintViolation<T> violation : violations) {
            String fieldName = violation.getPropertyPath().toString();
            String errorCode = violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName();
            String message = violation.getMessage();
            Object rejectedValue = violation.getInvalidValue();
            
            errors.add(ValidationError.of(fieldName, errorCode, message, rejectedValue));
        }
        
        return errors;
    }

    /**
     * Create a ValidationError for a specific field
     */
    public static ValidationError createFieldError(String field, String message) {
        return ValidationError.of(field, message);
    }

    /**
     * Create a ValidationError with error code
     */
    public static ValidationError createFieldError(String field, String code, String message) {
        return ValidationError.of(field, code, message);
    }

    /**
     * Create a ValidationError with rejected value
     */
    public static ValidationError createFieldError(String field, String code, String message, Object rejectedValue) {
        return ValidationError.of(field, code, message, rejectedValue);
    }
}