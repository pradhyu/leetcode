package com.microservice.utilities.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a validation error in API responses.
 * Used to provide detailed information about field validation failures.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationError {

    @JsonProperty("field")
    private String field;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("rejectedValue")
    private Object rejectedValue;

    // Constructors
    public ValidationError() {
    }

    public ValidationError(String field, String message) {
        this.field = field;
        this.message = message;
    }

    public ValidationError(String field, String code, String message) {
        this.field = field;
        this.code = code;
        this.message = message;
    }

    public ValidationError(String field, String code, String message, Object rejectedValue) {
        this.field = field;
        this.code = code;
        this.message = message;
        this.rejectedValue = rejectedValue;
    }

    // Static factory methods
    public static ValidationError of(String field, String message) {
        return new ValidationError(field, message);
    }

    public static ValidationError of(String field, String code, String message) {
        return new ValidationError(field, code, message);
    }

    public static ValidationError of(String field, String code, String message, Object rejectedValue) {
        return new ValidationError(field, code, message, rejectedValue);
    }

    // Getters and Setters
    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }

    public void setRejectedValue(Object rejectedValue) {
        this.rejectedValue = rejectedValue;
    }

    @Override
    public String toString() {
        return "ValidationError{" +
                "field='" + field + '\'' +
                ", code='" + code + '\'' +
                ", message='" + message + '\'' +
                ", rejectedValue=" + rejectedValue +
                '}';
    }
}