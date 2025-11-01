package com.microservice.utilities.data.audit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Configuration for JPA auditing functionality.
 * Automatically populates audit fields (createdBy, updatedBy) based on current user.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class AuditingConfig {

    /**
     * Provides the current auditor (user) for automatic auditing
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return new SpringSecurityAuditorAware();
    }

    /**
     * AuditorAware implementation that uses Spring Security to get current user
     */
    public static class SpringSecurityAuditorAware implements AuditorAware<String> {

        @Override
        public Optional<String> getCurrentAuditor() {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null || !authentication.isAuthenticated() || 
                "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of("system");
            }
            
            // Handle different authentication principal types
            Object principal = authentication.getPrincipal();
            
            if (principal instanceof String) {
                return Optional.of((String) principal);
            }
            
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return Optional.of(((org.springframework.security.core.userdetails.UserDetails) principal).getUsername());
            }
            
            // Fallback to authentication name
            return Optional.of(authentication.getName());
        }
    }

    /**
     * Custom AuditorAware that can be used when Spring Security is not available
     */
    public static class ThreadLocalAuditorAware implements AuditorAware<String> {
        
        private static final ThreadLocal<String> currentAuditor = new ThreadLocal<>();
        
        public static void setCurrentAuditor(String auditor) {
            currentAuditor.set(auditor);
        }
        
        public static void clearCurrentAuditor() {
            currentAuditor.remove();
        }
        
        @Override
        public Optional<String> getCurrentAuditor() {
            String auditor = currentAuditor.get();
            return Optional.ofNullable(auditor != null ? auditor : "system");
        }
    }

    /**
     * Header-based AuditorAware for microservices that pass user info via headers
     */
    public static class HeaderBasedAuditorAware implements AuditorAware<String> {
        
        private static final String USER_HEADER = "X-User-Id";
        private static final ThreadLocal<String> currentUser = new ThreadLocal<>();
        
        public static void setCurrentUser(String userId) {
            currentUser.set(userId);
        }
        
        public static void clearCurrentUser() {
            currentUser.remove();
        }
        
        @Override
        public Optional<String> getCurrentAuditor() {
            String userId = currentUser.get();
            return Optional.ofNullable(userId != null ? userId : "system");
        }
    }
}