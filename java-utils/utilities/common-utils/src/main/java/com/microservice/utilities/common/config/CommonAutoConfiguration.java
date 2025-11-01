package com.microservice.utilities.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Auto-configuration for common utilities.
 * Enables JPA auditing and configuration properties.
 */
@Configuration
@EnableConfigurationProperties(ApplicationProperties.class)
@EnableJpaAuditing
public class CommonAutoConfiguration {
    // Configuration class for auto-configuration
}