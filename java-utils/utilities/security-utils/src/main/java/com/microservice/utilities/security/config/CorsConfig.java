package com.microservice.utilities.security.config;

import com.microservice.utilities.common.config.ApplicationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

/**
 * CORS configuration with environment-specific policies.
 */
@Configuration
@ConditionalOnProperty(name = "app.security.enable-cors", havingValue = "true", matchIfMissing = true)
public class CorsConfig implements WebMvcConfigurer {

    private final ApplicationProperties applicationProperties;

    public CorsConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (applicationProperties.getSecurity().isEnableCors()) {
            registry.addMapping("/**")
                    .allowedOriginPatterns(applicationProperties.getSecurity().getAllowedOrigins())
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }

    /**
     * CORS configuration source for Spring Security
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        if (applicationProperties.getSecurity().isEnableCors()) {
            // Development configuration - more permissive
            if (isDevelopmentEnvironment()) {
                configuration.setAllowedOriginPatterns(Arrays.asList("*"));
                configuration.setAllowedMethods(Arrays.asList("*"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setAllowCredentials(true);
            } else {
                // Production configuration - restrictive
                configuration.setAllowedOriginPatterns(Arrays.asList(applicationProperties.getSecurity().getAllowedOrigins()));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList(
                    "Authorization", "Content-Type", "X-Requested-With", "Accept", 
                    "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"
                ));
                configuration.setExposedHeaders(Arrays.asList(
                    "Access-Control-Allow-Origin", "Access-Control-Allow-Credentials"
                ));
                configuration.setAllowCredentials(true);
            }
            
            configuration.setMaxAge(3600L);
        } else {
            // CORS disabled
            configuration.setAllowedOrigins(List.of());
        }
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Strict CORS configuration for production APIs
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.cors.strict", havingValue = "true")
    public CorsConfigurationSource strictCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Only allow specific origins
        configuration.setAllowedOrigins(Arrays.asList(applicationProperties.getSecurity().getAllowedOrigins()));
        
        // Only allow specific methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE"));
        
        // Only allow specific headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "Accept"
        ));
        
        // Don't allow credentials for strict mode
        configuration.setAllowCredentials(false);
        
        // Shorter cache time
        configuration.setMaxAge(1800L); // 30 minutes
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Check if running in development environment
     */
    private boolean isDevelopmentEnvironment() {
        String[] activeProfiles = System.getProperty("spring.profiles.active", "").split(",");
        return Arrays.asList(activeProfiles).contains("dev") || 
               Arrays.asList(activeProfiles).contains("development") ||
               Arrays.asList(activeProfiles).contains("local");
    }
}