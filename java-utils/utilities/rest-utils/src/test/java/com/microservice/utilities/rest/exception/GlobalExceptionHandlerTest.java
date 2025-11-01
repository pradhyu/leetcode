package com.microservice.utilities.rest.exception;

import com.microservice.utilities.common.dto.ApiResponse;
import com.microservice.utilities.common.dto.ValidationError;
import com.microservice.utilities.common.exception.BusinessException;
import com.microservice.utilities.common.exception.ResourceNotFoundException;
import com.microservice.utilities.common.exception.UnauthorizedException;
import com.microservice.utilities.common.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(request.getRequestURI()).thenReturn("/test");
    }

    @Test
    void handleBusinessException_ShouldReturnErrorResponse() {
        // Given
        BusinessException exception = new ResourceNotFoundException("Resource not found");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleBusinessException(exception, request);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Resource not found", response.getBody().getMessage());
    }

    @Test
    void handleValidationException_ShouldReturnBadRequestWithErrors() {
        // Given
        List<ValidationError> errors = Arrays.asList(
                ValidationError.of("field1", "Field1 is required"),
                ValidationError.of("field2", "Field2 is invalid")
        );
        ValidationException exception = new ValidationException("Validation failed", errors);

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleValidationException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Validation failed", response.getBody().getMessage());
    }

    @Test
    void handleResourceNotFoundException_ShouldReturnNotFound() {
        // Given
        ResourceNotFoundException exception = new ResourceNotFoundException("User not found");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleResourceNotFoundException(exception, request);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found", response.getBody().getMessage());
    }

    @Test
    void handleUnauthorizedException_ShouldReturnUnauthorized() {
        // Given
        UnauthorizedException exception = UnauthorizedException.invalidToken();

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleUnauthorizedException(exception, request);

        // Then
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid or expired authentication token", response.getBody().getMessage());
    }

    @Test
    void handleMethodArgumentNotValid_ShouldReturnBadRequestWithValidationErrors() {
        // Given
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        org.springframework.validation.BindingResult bindingResult = mock(org.springframework.validation.BindingResult.class);
        
        FieldError fieldError = new FieldError("testObject", "testField", "rejectedValue", false, null, null, "Test field is required");
        when(bindingResult.getAllErrors()).thenReturn(Arrays.asList(fieldError));
        when(exception.getBindingResult()).thenReturn(bindingResult);

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleMethodArgumentNotValid(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Validation failed", response.getBody().getMessage());
    }

    @Test
    void handleConstraintViolationException_ShouldReturnBadRequestWithConstraintErrors() {
        // Given
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getPropertyPath().toString()).thenReturn("testField");
        when(violation.getMessage()).thenReturn("Test constraint violation");
        when(violation.getInvalidValue()).thenReturn("invalidValue");

        Set<ConstraintViolation<?>> violations = new HashSet<>();
        violations.add(violation);

        ConstraintViolationException exception = new ConstraintViolationException("Constraint violation", violations);

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleConstraintViolationException(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Constraint validation failed", response.getBody().getMessage());
    }

    @Test
    void handleHttpMessageNotReadable_ShouldReturnBadRequest() {
        // Given
        HttpMessageNotReadableException exception = mock(HttpMessageNotReadableException.class);
        when(exception.getMessage()).thenReturn("JSON parse error");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleHttpMessageNotReadable(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Invalid request body format", response.getBody().getMessage());
    }

    @Test
    void handleMethodArgumentTypeMismatch_ShouldReturnBadRequest() {
        // Given
        MethodArgumentTypeMismatchException exception = mock(MethodArgumentTypeMismatchException.class);
        when(exception.getName()).thenReturn("id");
        when(exception.getValue()).thenReturn("invalid");
        when(exception.getRequiredType()).thenReturn((Class) Long.class);

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleMethodArgumentTypeMismatch(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertTrue(response.getBody().getMessage().contains("Invalid value 'invalid' for parameter 'id'"));
    }

    @Test
    void handleMissingServletRequestParameter_ShouldReturnBadRequest() {
        // Given
        MissingServletRequestParameterException exception = new MissingServletRequestParameterException("requiredParam", "String");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleMissingServletRequestParameter(exception, request);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("Required parameter 'requiredParam' is missing", response.getBody().getMessage());
    }

    @Test
    void handleHttpRequestMethodNotSupported_ShouldReturnMethodNotAllowed() {
        // Given
        HttpRequestMethodNotSupportedException exception = new HttpRequestMethodNotSupportedException("POST");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleHttpRequestMethodNotSupported(exception, request);

        // Then
        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("HTTP method 'POST' is not supported for this endpoint", response.getBody().getMessage());
    }

    @Test
    void handleGenericException_ShouldReturnInternalServerError() {
        // Given
        Exception exception = new RuntimeException("Unexpected error");

        // When
        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleGenericException(exception, request);

        // Then
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("An unexpected error occurred. Please try again later.", response.getBody().getMessage());
    }
}