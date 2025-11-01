package com.microservice.utilities.rest.config;

import com.microservice.utilities.rest.exception.GlobalExceptionHandler;
import com.microservice.utilities.rest.validation.ValidationHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Auto-configuration for REST utilities.
 * Automatically configures REST-related beans when this module is on the classpath.
 */
@Configuration
@Import({
        WebConfig.class,
        OpenApiConfig.class,
        GlobalExceptionHandler.class
})
public class RestAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ValidationHandler validationHandler(jakarta.validation.Validator validator) {
        return new ValidationHandler(validator);
    }
}