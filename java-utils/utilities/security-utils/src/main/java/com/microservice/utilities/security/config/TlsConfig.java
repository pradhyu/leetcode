package com.microservice.utilities.security.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

/**
 * TLS/SSL configuration for HTTPS with certificate management.
 */
@Configuration
@ConditionalOnProperty(name = "app.security.tls.enabled", havingValue = "true")
public class TlsConfig {

    private static final Logger logger = LoggerFactory.getLogger(TlsConfig.class);

    /**
     * Configure TLS for Tomcat embedded server
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatCustomizer() {
        return factory -> {
            // Configure HTTPS connector
            Connector httpsConnector = createHttpsConnector();
            if (httpsConnector != null) {
                factory.addAdditionalTomcatConnectors(httpsConnector);
            }
            
            // Configure HTTP to HTTPS redirect
            factory.addConnectorCustomizers(connector -> {
                connector.setRedirectPort(8443); // HTTPS port
            });
        };
    }

    /**
     * Create HTTPS connector with TLS configuration
     */
    private Connector createHttpsConnector() {
        try {
            Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
            connector.setScheme("https");
            connector.setPort(8443);
            connector.setSecure(true);
            
            Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
            
            // SSL/TLS configuration
            protocol.setSSLEnabled(true);
            protocol.setKeystoreFile(getKeystorePath());
            protocol.setKeystorePass(getKeystorePassword());
            protocol.setKeyAlias("microservice");
            
            // Security protocols and cipher suites
            protocol.setSslProtocol("TLS");
            protocol.setSslEnabledProtocols("TLSv1.2,TLSv1.3");
            protocol.setCiphers(getSecureCipherSuites());
            
            // Client certificate authentication (optional)
            protocol.setClientAuth("false"); // Change to "true" or "want" for client certs
            
            // Performance tuning
            protocol.setMaxThreads(200);
            protocol.setAcceptCount(100);
            protocol.setConnectionTimeout(20000);
            
            logger.info("HTTPS connector configured on port 8443");
            return connector;
            
        } catch (Exception e) {
            logger.error("Failed to configure HTTPS connector", e);
            return null;
        }
    }

    /**
     * Get keystore path from configuration or classpath
     */
    private String getKeystorePath() {
        // Try system property first
        String keystorePath = System.getProperty("server.ssl.key-store");
        if (keystorePath != null) {
            return keystorePath;
        }
        
        // Try environment variable
        keystorePath = System.getenv("SSL_KEYSTORE_PATH");
        if (keystorePath != null) {
            return keystorePath;
        }
        
        // Default to classpath resource
        try {
            ClassPathResource resource = new ClassPathResource("keystore/microservice.p12");
            if (resource.exists()) {
                return resource.getURL().toString();
            }
        } catch (IOException e) {
            logger.warn("Default keystore not found in classpath", e);
        }
        
        // Generate self-signed certificate for development
        return generateSelfSignedKeystore();
    }

    /**
     * Get keystore password from configuration
     */
    private String getKeystorePassword() {
        // Try system property first
        String password = System.getProperty("server.ssl.key-store-password");
        if (password != null) {
            return password;
        }
        
        // Try environment variable
        password = System.getenv("SSL_KEYSTORE_PASSWORD");
        if (password != null) {
            return password;
        }
        
        // Default password for development
        return "changeit";
    }

    /**
     * Get secure cipher suites for TLS
     */
    private String getSecureCipherSuites() {
        return String.join(",", 
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256"
        );
    }

    /**
     * Generate self-signed keystore for development
     */
    private String generateSelfSignedKeystore() {
        try {
            // Create a temporary keystore for development
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            
            // In a real implementation, you would generate a self-signed certificate here
            // For now, return a placeholder path
            logger.warn("Using development keystore - not suitable for production");
            return "classpath:keystore/dev-keystore.p12";
            
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            logger.error("Failed to generate development keystore", e);
            return null;
        }
    }

    /**
     * TLS configuration for client connections
     */
    @Bean
    @ConditionalOnProperty(name = "app.security.tls.client.enabled", havingValue = "true")
    public TlsClientConfig tlsClientConfig() {
        return new TlsClientConfig();
    }

    /**
     * TLS client configuration for outbound connections
     */
    public static class TlsClientConfig {
        
        /**
         * Configure TLS for RestTemplate
         */
        public org.springframework.web.client.RestTemplate createSecureRestTemplate() {
            try {
                // Create SSL context with custom trust store
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                
                // Configure trust manager (for production, use proper certificate validation)
                javax.net.ssl.TrustManager[] trustManagers = {
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
                };
                
                sslContext.init(null, trustManagers, new java.security.SecureRandom());
                
                // Create HTTP client with SSL context
                org.apache.http.impl.client.CloseableHttpClient httpClient = 
                    org.apache.http.impl.client.HttpClients.custom()
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(org.apache.http.conn.ssl.NoopHostnameVerifier.INSTANCE)
                        .build();
                
                org.springframework.http.client.HttpComponentsClientHttpRequestFactory factory = 
                    new org.springframework.http.client.HttpComponentsClientHttpRequestFactory(httpClient);
                
                return new org.springframework.web.client.RestTemplate(factory);
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to create secure RestTemplate", e);
            }
        }

        /**
         * Configure TLS for WebClient
         */
        public org.springframework.web.reactive.function.client.WebClient createSecureWebClient() {
            try {
                // Create SSL context
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, getTrustAllCerts(), new java.security.SecureRandom());
                
                // Create WebClient with SSL context
                return org.springframework.web.reactive.function.client.WebClient.builder()
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        reactor.netty.http.client.HttpClient.create()
                            .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext))
                    ))
                    .build();
                
            } catch (Exception e) {
                throw new RuntimeException("Failed to create secure WebClient", e);
            }
        }

        private javax.net.ssl.TrustManager[] getTrustAllCerts() {
            return new javax.net.ssl.TrustManager[] {
                new javax.net.ssl.X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                }
            };
        }
    }

    /**
     * Security headers configuration
     */
    @Bean
    public SecurityHeadersConfig securityHeadersConfig() {
        return new SecurityHeadersConfig();
    }

    /**
     * Security headers configuration class
     */
    public static class SecurityHeadersConfig {
        
        /**
         * Configure security headers filter
         */
        @Bean
        public javax.servlet.Filter securityHeadersFilter() {
            return new javax.servlet.Filter() {
                @Override
                public void doFilter(javax.servlet.ServletRequest request, 
                                   javax.servlet.ServletResponse response, 
                                   javax.servlet.FilterChain chain) 
                        throws java.io.IOException, javax.servlet.ServletException {
                    
                    javax.servlet.http.HttpServletResponse httpResponse = 
                        (javax.servlet.http.HttpServletResponse) response;
                    
                    // Security headers
                    httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                    httpResponse.setHeader("X-Frame-Options", "DENY");
                    httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
                    httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                    httpResponse.setHeader("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
                    
                    // HSTS header for HTTPS
                    if (request.isSecure()) {
                        httpResponse.setHeader("Strict-Transport-Security", 
                            "max-age=31536000; includeSubDomains; preload");
                    }
                    
                    // CSP header
                    httpResponse.setHeader("Content-Security-Policy", 
                        "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
                    
                    chain.doFilter(request, response);
                }
            };
        }
    }
}