package com.microservice.utilities.rest.validation;

import com.microservice.utilities.rest.validation.CustomValidators.*;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomValidatorsTest {

    @Mock
    private ConstraintValidatorContext context;

    @Test
    void alphanumericValidator_WithValidAlphanumericString_ShouldReturnTrue() {
        // Given
        AlphanumericValidator validator = new AlphanumericValidator();
        Alphanumeric annotation = createAlphanumericAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid("abc123", context));
        assertTrue(validator.isValid("ABC123", context));
        assertTrue(validator.isValid("123", context));
        assertTrue(validator.isValid("abc", context));
    }

    @Test
    void alphanumericValidator_WithInvalidCharacters_ShouldReturnFalse() {
        // Given
        AlphanumericValidator validator = new AlphanumericValidator();
        Alphanumeric annotation = createAlphanumericAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertFalse(validator.isValid("abc-123", context));
        assertFalse(validator.isValid("abc@123", context));
        assertFalse(validator.isValid("abc 123", context));
    }

    @Test
    void alphanumericValidator_WithSpacesAllowed_ShouldReturnTrue() {
        // Given
        AlphanumericValidator validator = new AlphanumericValidator();
        Alphanumeric annotation = createAlphanumericAnnotation(true);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid("abc 123", context));
        assertTrue(validator.isValid("hello world", context));
    }

    @Test
    void alphanumericValidator_WithNullValue_ShouldReturnTrue() {
        // Given
        AlphanumericValidator validator = new AlphanumericValidator();
        Alphanumeric annotation = createAlphanumericAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void phoneNumberValidator_WithValidPhoneNumbers_ShouldReturnTrue() {
        // Given
        PhoneNumberValidator validator = new PhoneNumberValidator();

        // When & Then
        assertTrue(validator.isValid("+1234567890", context));
        assertTrue(validator.isValid("1234567890", context));
        assertTrue(validator.isValid("+44 20 7946 0958", context));
        assertTrue(validator.isValid("(555) 123-4567", context));
    }

    @Test
    void phoneNumberValidator_WithInvalidPhoneNumbers_ShouldReturnFalse() {
        // Given
        PhoneNumberValidator validator = new PhoneNumberValidator();

        // When & Then
        assertFalse(validator.isValid("12", context)); // Too short
        assertFalse(validator.isValid("abc123", context));
        assertFalse(validator.isValid("+", context));
        assertFalse(validator.isValid("", context));
    }

    @Test
    void phoneNumberValidator_WithNullValue_ShouldReturnTrue() {
        // Given
        PhoneNumberValidator validator = new PhoneNumberValidator();

        // When & Then
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void uuidValidator_WithValidUUIDs_ShouldReturnTrue() {
        // Given
        UUIDValidator validator = new UUIDValidator();

        // When & Then
        assertTrue(validator.isValid("123e4567-e89b-12d3-a456-426614174000", context));
        assertTrue(validator.isValid("550e8400-e29b-41d4-a716-446655440000", context));
        assertTrue(validator.isValid("6ba7b810-9dad-11d1-80b4-00c04fd430c8", context));
    }

    @Test
    void uuidValidator_WithInvalidUUIDs_ShouldReturnFalse() {
        // Given
        UUIDValidator validator = new UUIDValidator();

        // When & Then
        assertFalse(validator.isValid("123e4567-e89b-12d3-a456", context));
        assertFalse(validator.isValid("not-a-uuid", context));
        assertFalse(validator.isValid("123e4567e89b12d3a456426614174000", context));
        assertFalse(validator.isValid("", context));
    }

    @Test
    void uuidValidator_WithNullValue_ShouldReturnTrue() {
        // Given
        UUIDValidator validator = new UUIDValidator();

        // When & Then
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void positiveNumberValidator_WithPositiveNumbers_ShouldReturnTrue() {
        // Given
        PositiveNumberValidator validator = new PositiveNumberValidator();
        PositiveNumber annotation = createPositiveNumberAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid(1, context));
        assertTrue(validator.isValid(1.5, context));
        assertTrue(validator.isValid(100L, context));
    }

    @Test
    void positiveNumberValidator_WithZero_WhenZeroNotAllowed_ShouldReturnFalse() {
        // Given
        PositiveNumberValidator validator = new PositiveNumberValidator();
        PositiveNumber annotation = createPositiveNumberAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertFalse(validator.isValid(0, context));
        assertFalse(validator.isValid(0.0, context));
    }

    @Test
    void positiveNumberValidator_WithZero_WhenZeroAllowed_ShouldReturnTrue() {
        // Given
        PositiveNumberValidator validator = new PositiveNumberValidator();
        PositiveNumber annotation = createPositiveNumberAnnotation(true);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid(0, context));
        assertTrue(validator.isValid(0.0, context));
    }

    @Test
    void positiveNumberValidator_WithNegativeNumbers_ShouldReturnFalse() {
        // Given
        PositiveNumberValidator validator = new PositiveNumberValidator();
        PositiveNumber annotation = createPositiveNumberAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertFalse(validator.isValid(-1, context));
        assertFalse(validator.isValid(-1.5, context));
        assertFalse(validator.isValid(-100L, context));
    }

    @Test
    void positiveNumberValidator_WithNullValue_ShouldReturnTrue() {
        // Given
        PositiveNumberValidator validator = new PositiveNumberValidator();
        PositiveNumber annotation = createPositiveNumberAnnotation(false);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void patternMatchValidator_WithMatchingPattern_ShouldReturnTrue() {
        // Given
        PatternMatchValidator validator = new PatternMatchValidator();
        PatternMatch annotation = createPatternMatchAnnotation("^[A-Z]{2}\\d{3}$", 0);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid("AB123", context));
        assertTrue(validator.isValid("XY999", context));
    }

    @Test
    void patternMatchValidator_WithNonMatchingPattern_ShouldReturnFalse() {
        // Given
        PatternMatchValidator validator = new PatternMatchValidator();
        PatternMatch annotation = createPatternMatchAnnotation("^[A-Z]{2}\\d{3}$", 0);
        validator.initialize(annotation);

        // When & Then
        assertFalse(validator.isValid("ab123", context));
        assertFalse(validator.isValid("AB12", context));
        assertFalse(validator.isValid("ABC123", context));
    }

    @Test
    void patternMatchValidator_WithNullValue_ShouldReturnTrue() {
        // Given
        PatternMatchValidator validator = new PatternMatchValidator();
        PatternMatch annotation = createPatternMatchAnnotation("^[A-Z]{2}\\d{3}$", 0);
        validator.initialize(annotation);

        // When & Then
        assertTrue(validator.isValid(null, context));
    }

    // Helper methods to create mock annotations
    private Alphanumeric createAlphanumericAnnotation(boolean allowSpaces) {
        return new Alphanumeric() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Alphanumeric.class;
            }

            @Override
            public String message() {
                return "Field must contain only alphanumeric characters";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public boolean allowSpaces() {
                return allowSpaces;
            }
        };
    }

    private PositiveNumber createPositiveNumberAnnotation(boolean includeZero) {
        return new PositiveNumber() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return PositiveNumber.class;
            }

            @Override
            public String message() {
                return "Number must be positive";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public boolean includeZero() {
                return includeZero;
            }
        };
    }

    private PatternMatch createPatternMatchAnnotation(String pattern, int flags) {
        return new PatternMatch() {
            @Override
            public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return PatternMatch.class;
            }

            @Override
            public String message() {
                return "Field does not match required pattern";
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends jakarta.validation.Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public String pattern() {
                return pattern;
            }

            @Override
            public int flags() {
                return flags;
            }
        };
    }
}