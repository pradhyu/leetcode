package com.microservice.utilities.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;
import java.util.regex.Pattern;

/**
 * Custom validation annotations for common validation scenarios.
 */
public class CustomValidators {

    /**
     * Validates that a string contains only alphanumeric characters
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = AlphanumericValidator.class)
    @Documented
    public @interface Alphanumeric {
        String message() default "Field must contain only alphanumeric characters";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        boolean allowSpaces() default false;
    }

    public static class AlphanumericValidator implements ConstraintValidator<Alphanumeric, String> {
        private boolean allowSpaces;

        @Override
        public void initialize(Alphanumeric constraintAnnotation) {
            this.allowSpaces = constraintAnnotation.allowSpaces();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true; // Use @NotNull for null checks
            }
            
            String pattern = allowSpaces ? "^[a-zA-Z0-9\\s]+$" : "^[a-zA-Z0-9]+$";
            return Pattern.matches(pattern, value);
        }
    }

    /**
     * Validates phone number format
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PhoneNumberValidator.class)
    @Documented
    public @interface PhoneNumber {
        String message() default "Invalid phone number format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class PhoneNumberValidator implements ConstraintValidator<PhoneNumber, String> {
        private static final Pattern PHONE_PATTERN = Pattern.compile(
                "^[+]?[1-9]\\d{6,14}$" // E.164 format with minimum 7 digits
        );

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            String cleanedValue = value.replaceAll("[\\s()-]", "");
            return PHONE_PATTERN.matcher(cleanedValue).matches();
        }
    }

    /**
     * Validates that a string is a valid UUID
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = UUIDValidator.class)
    @Documented
    public @interface ValidUUID {
        String message() default "Invalid UUID format";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class UUIDValidator implements ConstraintValidator<ValidUUID, String> {
        private static final Pattern UUID_PATTERN = Pattern.compile(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return UUID_PATTERN.matcher(value).matches();
        }
    }

    /**
     * Validates that a number is positive
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PositiveNumberValidator.class)
    @Documented
    public @interface PositiveNumber {
        String message() default "Number must be positive";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        boolean includeZero() default false;
    }

    public static class PositiveNumberValidator implements ConstraintValidator<PositiveNumber, Number> {
        private boolean includeZero;

        @Override
        public void initialize(PositiveNumber constraintAnnotation) {
            this.includeZero = constraintAnnotation.includeZero();
        }

        @Override
        public boolean isValid(Number value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            
            double doubleValue = value.doubleValue();
            return includeZero ? doubleValue >= 0 : doubleValue > 0;
        }
    }

    /**
     * Validates that a string matches a specific pattern
     */
    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PatternMatchValidator.class)
    @Documented
    public @interface PatternMatch {
        String message() default "Field does not match required pattern";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
        String pattern();
        int flags() default 0;
    }

    public static class PatternMatchValidator implements ConstraintValidator<PatternMatch, String> {
        private Pattern pattern;

        @Override
        public void initialize(PatternMatch constraintAnnotation) {
            this.pattern = Pattern.compile(constraintAnnotation.pattern(), constraintAnnotation.flags());
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            return pattern.matcher(value).matches();
        }
    }
}