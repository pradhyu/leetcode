package com.microservice.utilities.security.jwt;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * JWT authentication filter for validating JWT tokens in requests.
 * Extracts and validates JWT tokens from Authorization header or cookies.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_COOKIE_NAME = "jwt-token";

    private final JwtTokenProvider tokenProvider;
    private final Counter validTokenCounter;
    private final Counter invalidTokenCounter;
    private final Counter missingTokenCounter;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, MeterRegistry meterRegistry) {
        this.tokenProvider = tokenProvider;
        this.validTokenCounter = Counter.builder("jwt.tokens.valid")
                .description("Number of valid JWT tokens processed")
                .register(meterRegistry);
        this.invalidTokenCounter = Counter.builder("jwt.tokens.invalid")
                .description("Number of invalid JWT tokens processed")
                .register(meterRegistry);
        this.missingTokenCounter = Counter.builder("jwt.tokens.missing")
                .description("Number of requests without JWT tokens")
                .register(meterRegistry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String jwt = extractJwtFromRequest(request);
            
            if (StringUtils.hasText(jwt)) {
                if (tokenProvider.validateToken(jwt)) {
                    setAuthenticationFromToken(jwt, request);
                    validTokenCounter.increment();
                    logger.debug("Valid JWT token found for user: {}", tokenProvider.getUsernameFromToken(jwt));
                } else {
                    invalidTokenCounter.increment();
                    logger.debug("Invalid JWT token received");
                    handleInvalidToken(response);
                    return;
                }
            } else {
                missingTokenCounter.increment();
                logger.debug("No JWT token found in request");
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication", e);
            invalidTokenCounter.increment();
            handleAuthenticationError(response, e);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from request (Authorization header or cookie)
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        // Try Authorization header first
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        // Try cookie as fallback
        if (request.getCookies() != null) {
            for (javax.servlet.http.Cookie cookie : request.getCookies()) {
                if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        return null;
    }

    /**
     * Set authentication in security context from JWT token
     */
    private void setAuthenticationFromToken(String jwt, HttpServletRequest request) {
        String username = tokenProvider.getUsernameFromToken(jwt);
        List<String> authorities = tokenProvider.getAuthoritiesFromToken(jwt);
        
        if (username != null && authorities != null) {
            List<SimpleGrantedAuthority> grantedAuthorities = authorities.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // Create custom user principal with additional info
            JwtUserPrincipal userPrincipal = new JwtUserPrincipal(
                username,
                tokenProvider.getUserIdFromToken(jwt),
                tokenProvider.getSessionIdFromToken(jwt),
                grantedAuthorities
            );

            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userPrincipal, null, grantedAuthorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
    }

    /**
     * Handle invalid token
     */
    private void handleInvalidToken(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Invalid or expired JWT token\"}");
    }

    /**
     * Handle authentication error
     */
    private void handleAuthenticationError(HttpServletResponse response, Exception e) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Authentication failed: " + e.getMessage() + "\"}");
    }

    /**
     * Skip JWT authentication for certain paths
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip authentication for public endpoints
        return path.startsWith("/public/") ||
               path.startsWith("/health") ||
               path.startsWith("/actuator/") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs") ||
               path.equals("/login") ||
               path.equals("/register") ||
               path.equals("/favicon.ico");
    }

    /**
     * Custom user principal with JWT-specific information
     */
    public static class JwtUserPrincipal {
        private final String username;
        private final String userId;
        private final String sessionId;
        private final List<SimpleGrantedAuthority> authorities;

        public JwtUserPrincipal(String username, String userId, String sessionId, 
                               List<SimpleGrantedAuthority> authorities) {
            this.username = username;
            this.userId = userId;
            this.sessionId = sessionId;
            this.authorities = authorities;
        }

        public String getUsername() { return username; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public List<SimpleGrantedAuthority> getAuthorities() { return authorities; }
        
        public boolean hasAuthority(String authority) {
            return authorities.stream()
                    .anyMatch(auth -> auth.getAuthority().equals(authority));
        }
        
        public boolean hasAnyAuthority(String... authorities) {
            for (String authority : authorities) {
                if (hasAuthority(authority)) {
                    return true;
                }
            }
            return false;
        }
    }
}