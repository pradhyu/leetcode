package com.microservice.utilities.security.jwt;

import com.microservice.utilities.common.config.ApplicationProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JWT token provider for creating and validating JWT tokens.
 * Supports configurable claims, signing algorithms, and token expiration.
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String AUTHORITIES_KEY = "authorities";
    private static final String USER_ID_KEY = "userId";
    private static final String SESSION_ID_KEY = "sessionId";

    private final ApplicationProperties applicationProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
        this.secretKey = Keys.hmacShaKeyFor(applicationProperties.getSecurity().getJwtSecret().getBytes());
    }

    /**
     * Generate JWT token from Authentication
     */
    public String generateToken(Authentication authentication) {
        UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
        
        List<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return generateToken(userPrincipal.getUsername(), authorities, null);
    }

    /**
     * Generate JWT token with custom claims
     */
    public String generateToken(String username, List<String> authorities, Map<String, Object> customClaims) {
        Instant now = Instant.now();
        Instant expiration = now.plus(applicationProperties.getSecurity().getJwtExpirationSeconds(), ChronoUnit.SECONDS);

        Map<String, Object> claims = new HashMap<>();
        claims.put(AUTHORITIES_KEY, authorities);
        
        if (customClaims != null) {
            claims.putAll(customClaims);
        }

        return Jwts.builder()
                .setSubject(username)
                .addClaims(claims)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Generate token with user ID and session ID
     */
    public String generateTokenWithUserInfo(String username, String userId, String sessionId, List<String> authorities) {
        Map<String, Object> customClaims = new HashMap<>();
        customClaims.put(USER_ID_KEY, userId);
        customClaims.put(SESSION_ID_KEY, sessionId);
        
        return generateToken(username, authorities, customClaims);
    }

    /**
     * Generate refresh token with longer expiration
     */
    public String generateRefreshToken(String username) {
        Instant now = Instant.now();
        Instant expiration = now.plus(7, ChronoUnit.DAYS); // 7 days for refresh token

        return Jwts.builder()
                .setSubject(username)
                .claim("type", "refresh")
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiration))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Validate JWT token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token);
            return true;
        } catch (SecurityException e) {
            logger.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Get username from JWT token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Get authorities from JWT token
     */
    @SuppressWarnings("unchecked")
    public List<String> getAuthoritiesFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (List<String>) claims.get(AUTHORITIES_KEY);
    }

    /**
     * Get user ID from JWT token
     */
    public String getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (String) claims.get(USER_ID_KEY);
    }

    /**
     * Get session ID from JWT token
     */
    public String getSessionIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return (String) claims.get(SESSION_ID_KEY);
    }

    /**
     * Get custom claim from JWT token
     */
    public Object getClaimFromToken(String token, String claimName) {
        Claims claims = getClaimsFromToken(token);
        return claims.get(claimName);
    }

    /**
     * Get all claims from JWT token
     */
    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Get token expiration date
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Get remaining time until token expires (in seconds)
     */
    public long getRemainingTimeInSeconds(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            long remainingTime = (expiration.getTime() - System.currentTimeMillis()) / 1000;
            return Math.max(0, remainingTime);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Refresh token if it's close to expiration
     */
    public String refreshTokenIfNeeded(String token, long refreshThresholdSeconds) {
        if (getRemainingTimeInSeconds(token) < refreshThresholdSeconds) {
            String username = getUsernameFromToken(token);
            List<String> authorities = getAuthoritiesFromToken(token);
            
            // Preserve custom claims
            Claims claims = getClaimsFromToken(token);
            Map<String, Object> customClaims = new HashMap<>();
            
            if (claims.get(USER_ID_KEY) != null) {
                customClaims.put(USER_ID_KEY, claims.get(USER_ID_KEY));
            }
            if (claims.get(SESSION_ID_KEY) != null) {
                customClaims.put(SESSION_ID_KEY, claims.get(SESSION_ID_KEY));
            }
            
            return generateToken(username, authorities, customClaims);
        }
        return token;
    }

    /**
     * Create token info object
     */
    public TokenInfo createTokenInfo(String token) {
        if (!validateToken(token)) {
            return null;
        }

        Claims claims = getClaimsFromToken(token);
        return new TokenInfo(
            claims.getSubject(),
            getAuthoritiesFromToken(token),
            getUserIdFromToken(token),
            getSessionIdFromToken(token),
            claims.getIssuedAt(),
            claims.getExpiration(),
            getRemainingTimeInSeconds(token)
        );
    }

    /**
     * Token information holder
     */
    public static class TokenInfo {
        private final String username;
        private final List<String> authorities;
        private final String userId;
        private final String sessionId;
        private final Date issuedAt;
        private final Date expiration;
        private final long remainingTimeSeconds;

        public TokenInfo(String username, List<String> authorities, String userId, String sessionId,
                        Date issuedAt, Date expiration, long remainingTimeSeconds) {
            this.username = username;
            this.authorities = authorities;
            this.userId = userId;
            this.sessionId = sessionId;
            this.issuedAt = issuedAt;
            this.expiration = expiration;
            this.remainingTimeSeconds = remainingTimeSeconds;
        }

        // Getters
        public String getUsername() { return username; }
        public List<String> getAuthorities() { return authorities; }
        public String getUserId() { return userId; }
        public String getSessionId() { return sessionId; }
        public Date getIssuedAt() { return issuedAt; }
        public Date getExpiration() { return expiration; }
        public long getRemainingTimeSeconds() { return remainingTimeSeconds; }
        public boolean isExpired() { return remainingTimeSeconds <= 0; }
    }
}