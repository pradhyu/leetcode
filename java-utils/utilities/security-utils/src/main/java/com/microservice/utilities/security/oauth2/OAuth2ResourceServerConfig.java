package com.microservice.utilities.security.oauth2;

import com.microservice.utilities.common.config.ApplicationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * OAuth2 Resource Server configuration for multiple providers.
 * Supports JWT token validation from external OAuth2 providers.
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "app.security.oauth2.enabled", havingValue = "true")
public class OAuth2ResourceServerConfig {

    private final ApplicationProperties applicationProperties;

    public OAuth2ResourceServerConfig(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    /**
     * OAuth2 Resource Server security filter chain
     */
    @Bean
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .authorizeHttpRequests(authz -> authz
                // Public endpoints
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/health/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                
                // OAuth2 protected endpoints
                .requestMatchers("/oauth2/**").authenticated()
                .requestMatchers("/api/oauth2/**").authenticated()
                
                // Require specific scopes for certain endpoints
                .requestMatchers("/api/admin/**").hasAuthority("SCOPE_admin")
                .requestMatchers("/api/user/**").hasAuthority("SCOPE_user")
                
                .anyRequest().authenticated()
            );

        return http.build();
    }

    /**
     * JWT Decoder for validating JWT tokens from OAuth2 provider
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // Configure for multiple issuers if needed
        String issuerUri = getIssuerUri();
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    /**
     * JWT Authentication Converter for mapping claims to authorities
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        
        // Configure authority prefix and claim name
        authoritiesConverter.setAuthorityPrefix("SCOPE_");
        authoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        
        // Set principal claim name (default is 'sub')
        authenticationConverter.setPrincipalClaimName("preferred_username");
        
        return authenticationConverter;
    }

    /**
     * Custom JWT Authentication Converter for complex claim mapping
     */
    @Bean
    public JwtAuthenticationConverter customJwtAuthenticationConverter() {
        return new JwtAuthenticationConverter() {
            @Override
            protected org.springframework.security.core.Authentication extractAuthentication(
                    org.springframework.security.oauth2.jwt.Jwt jwt) {
                
                org.springframework.security.core.Authentication authentication = super.extractAuthentication(jwt);
                
                // Add custom logic for extracting user information
                String username = jwt.getClaimAsString("preferred_username");
                if (username == null) {
                    username = jwt.getClaimAsString("email");
                }
                if (username == null) {
                    username = jwt.getSubject();
                }
                
                // Create custom principal with additional claims
                OAuth2UserPrincipal principal = new OAuth2UserPrincipal(
                    username,
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("sub"),
                    authentication.getAuthorities()
                );
                
                return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, jwt, authentication.getAuthorities());
            }
        };
    }

    /**
     * Multi-tenant JWT Decoder for handling multiple OAuth2 providers
     */
    @Bean
    public MultiTenantJwtDecoder multiTenantJwtDecoder() {
        return new MultiTenantJwtDecoder();
    }

    /**
     * Get issuer URI from configuration
     */
    private String getIssuerUri() {
        // Default to a common OAuth2 provider or from configuration
        return "https://accounts.google.com"; // Example - should come from config
    }

    /**
     * Custom OAuth2 User Principal
     */
    public static class OAuth2UserPrincipal {
        private final String username;
        private final String email;
        private final String name;
        private final String subject;
        private final java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities;

        public OAuth2UserPrincipal(String username, String email, String name, String subject,
                                  java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> authorities) {
            this.username = username;
            this.email = email;
            this.name = name;
            this.subject = subject;
            this.authorities = authorities;
        }

        // Getters
        public String getUsername() { return username; }
        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getSubject() { return subject; }
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() { 
            return authorities; 
        }
        
        public boolean hasAuthority(String authority) {
            return authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(authority));
        }
    }

    /**
     * Multi-tenant JWT Decoder for handling multiple OAuth2 providers
     */
    public static class MultiTenantJwtDecoder implements JwtDecoder {
        private final java.util.Map<String, JwtDecoder> decoders = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public org.springframework.security.oauth2.jwt.Jwt decode(String token) 
                throws org.springframework.security.oauth2.jwt.JwtException {
            
            // Extract issuer from token to determine which decoder to use
            String issuer = extractIssuerFromToken(token);
            
            JwtDecoder decoder = decoders.computeIfAbsent(issuer, 
                iss -> JwtDecoders.fromIssuerLocation(iss));
            
            return decoder.decode(token);
        }

        private String extractIssuerFromToken(String token) {
            // Simple JWT parsing to extract issuer claim
            // In production, use a proper JWT library
            try {
                String[] parts = token.split("\\.");
                if (parts.length >= 2) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    // Parse JSON to extract 'iss' claim
                    // This is a simplified implementation
                    if (payload.contains("\"iss\"")) {
                        int start = payload.indexOf("\"iss\":\"") + 7;
                        int end = payload.indexOf("\"", start);
                        return payload.substring(start, end);
                    }
                }
            } catch (Exception e) {
                // Log error and use default
            }
            
            // Default issuer
            return "https://accounts.google.com";
        }
    }

    /**
     * OAuth2 Token Introspection for opaque tokens
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.oauth2.introspection.enabled", havingValue = "true")
    public org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector 
            opaqueTokenIntrospector() {
        
        String introspectionUri = "https://oauth2-provider.com/introspect";
        String clientId = "client-id";
        String clientSecret = "client-secret";
        
        return new org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector(
            introspectionUri, clientId, clientSecret);
    }
}