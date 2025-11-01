package com.microservice.utilities.rest.validation;

import com.microservice.utilities.common.dto.ValidationError;
import com.microservice.utilities.common.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValidationHandlerTest {

    @Mock
    private Validator validator;

    @Mock
    private ConstraintViolation<Object> violation;

    private ValidationHandler validationHandler;

    @BeforeEach
    void setUp() {
        validationHandler = new ValidationHandler(validator);
    }

    @Test
    void validateAndThrow_WhenNoViolations_ShouldNotThrowException() {
        // Given
        Object testObject = new Object();
        when(validator.validate(testObject)).thenReturn(new HashSet<>());

        // When & Then
        assertDoesNotThrow(() -> validationHandler.validateAndThrow(testObject));
        verify(validator).validate(testObject);
    }

    @Test
    void validateAndThrow_WhenViolationsExist_ShouldThrowValidationException() {
        // Given
        Object testObject = new Object();
        Set<ConstraintViolation<Object>> violations = new HashSet<>();
        violations.add(violation);

        when(validator.validate(testObject)).thenReturn(violations);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("testField");
        when(violation.getMessage()).thenReturn("Test validation message");
        when(violation.getInvalidValue()).thenReturn("invalidValue");
        when(violation.getConstraintDescriptor()).thenReturn(mock(jakarta.validation.metadata.ConstraintDescriptor.class));
        when(violation.getConstraintDescriptor().getAnnotation()).thenReturn(mock(java.lang.annotation.Annotation.class));
        when(violation.getConstraintDescriptor().getAnnotation().annotationType()).thenReturn((Class) jakarta.validation.constraints.NotNull.class);

        // When & Then
        ValidationException exception = assertThrows(ValidationException.class, 
                () -> validationHandler.validateAndThrow(testObject));
        
        assertEquals("Validation failed", exception.getMessage());
        assertTrue(exception.hasValidationErrors());
        verify(validator).validate(testObject);
    }

    @Test
    void validate_WhenNoViolations_ShouldReturnEmptyList() {
        // Given
        Object testObject = new Object();
        when(validator.validate(testObject)).thenReturn(new HashSet<>());

        // When
        List<ValidationError> errors = validationHandler.validate(testObject);

        // Then
        assertTrue(errors.isEmpty());
        verify(validator).validate(testObject);
    }

    @Test
    void validate_WhenViolationsExist_ShouldReturnValidationErrors() {
        // Given
        Object testObject = new Object();
        Set<ConstraintViolation<Object>> violations = new HashSet<>();
        violations.add(violation);

        when(validator.validate(testObject)).thenReturn(violations);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("testField");
        when(violation.getMessage()).thenReturn("Test validation message");
        when(violation.getInvalidValue()).thenReturn("invalidValue");
        when(violation.getConstraintDescriptor()).thenReturn(mock(jakarta.validation.metadata.ConstraintDescriptor.class));
        when(violation.getConstraintDescriptor().getAnnotation()).thenReturn(mock(java.lang.annotation.Annotation.class));
        when(violation.getConstraintDescriptor().getAnnotation().annotationType()).thenReturn((Class) jakarta.validation.constraints.NotNull.class);

        // When
        List<ValidationError> errors = validationHandler.validate(testObject);

        // Then
        assertFalse(errors.isEmpty());
        assertEquals(1, errors.size());
        assertEquals("testField", errors.get(0).getField());
        assertEquals("Test validation message", errors.get(0).getMessage());
        verify(validator).validate(testObject);
    }

    @Test
    void validateProperty_ShouldCallValidatorValidateProperty() {
        // Given
        Object testObject = new Object();
        String propertyName = "testProperty";
        when(validator.validateProperty(testObject, propertyName)).thenReturn(new HashSet<>());

        // When
        List<ValidationError> errors = validationHandler.validateProperty(testObject, propertyName);

        // Then
        assertTrue(errors.isEmpty());
        verify(validator).validateProperty(testObject, propertyName);
    }

    @Test
    void validateValue_ShouldCallValidatorValidateValue() {
        // Given
        Class<Object> beanType = Object.class;
        String propertyName = "testProperty";
        Object value = "testValue";
        when(validator.validateValue(beanType, propertyName, value)).thenReturn(new HashSet<>());

        // When
        List<ValidationError> errors = validationHandler.validateValue(beanType, propertyName, value);

        // Then
        assertTrue(errors.isEmpty());
        verify(validator).validateValue(beanType, propertyName, value);
    }

    @Test
    void isValid_WhenNoViolations_ShouldReturnTrue() {
        // Given
        Object testObject = new Object();
        when(validator.validate(testObject)).thenReturn(new HashSet<>());

        // When
        boolean isValid = validationHandler.isValid(testObject);

        // Then
        assertTrue(isValid);
        verify(validator).validate(testObject);
    }

    @Test
    void isValid_WhenViolationsExist_ShouldReturnFalse() {
        // Given
        Object testObject = new Object();
        Set<ConstraintViolation<Object>> violations = new HashSet<>();
        violations.add(violation);
        when(validator.validate(testObject)).thenReturn(violations);

        // When
        boolean isValid = validationHandler.isValid(testObject);

        // Then
        assertFalse(isValid);
        verify(validator).validate(testObject);
    }

    @Test
    void createFieldError_WithFieldAndMessage_ShouldCreateValidationError() {
        // When
        ValidationError error = ValidationHandler.createFieldError("testField", "Test message");

        // Then
        assertEquals("testField", error.getField());
        assertEquals("Test message", error.getMessage());
        assertNull(error.getCode());
        assertNull(error.getRejectedValue());
    }

    @Test
    void createFieldError_WithFieldCodeAndMessage_ShouldCreateValidationError() {
        // When
        ValidationError error = ValidationHandler.createFieldError("testField", "TEST_CODE", "Test message");

        // Then
        assertEquals("testField", error.getField());
        assertEquals("TEST_CODE", error.getCode());
        assertEquals("Test message", error.getMessage());
        assertNull(error.getRejectedValue());
    }

    @Test
    void createFieldError_WithAllParameters_ShouldCreateValidationError() {
        // When
        ValidationError error = ValidationHandler.createFieldError("testField", "TEST_CODE", "Test message", "rejectedValue");

        // Then
        assertEquals("testField", error.getField());
        assertEquals("TEST_CODE", error.getCode());
        assertEquals("Test message", error.getMessage());
        assertEquals("rejectedValue", error.getRejectedValue());
    }
}