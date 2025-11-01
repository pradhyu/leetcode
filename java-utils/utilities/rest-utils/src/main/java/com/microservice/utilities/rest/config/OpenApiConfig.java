package com.microservice.utilities.rest.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for REST API documentation.
 * Provides comprehensive API documentation with security schemes.
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.name:Microservice API}")
    private String appName;

    @Value("${app.description:Microservice API Documentation}")
    private String appDescription;

    @Value("${app.version:1.0.0}")
    private String appVersion;

    @Value("${app.contact.name:Development Team}")
    private String contactName;

    @Value("${app.contact.email:dev@example.com}")
    private String contactEmail;

    @Value("${app.contact.url:https://example.com}")
    private String contactUrl;

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:" + serverPort).description("Local Development Server"),
                        new Server().url("https://api-dev.example.com").description("Development Server"),
                        new Server().url("https://api-staging.example.com").description("Staging Server"),
                        new Server().url("https://api.example.com").description("Production Server")
                ))
                .components(securityComponents())
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .addSecurityItem(new SecurityRequirement().addList("API Key Authentication"));
    }

    private Info apiInfo() {
        return new Info()
                .title(appName)
                .description(appDescription)
                .version(appVersion)
                .contact(new Contact()
                        .name(contactName)
                        .email(contactEmail)
                        .url(contactUrl))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    private Components securityComponents() {
        return new Components()
                .addSecuritySchemes("Bearer Authentication",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT Bearer token authentication"))
                .addSecuritySchemes("API Key Authentication",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key authentication"))
                .addSecuritySchemes("Basic Authentication",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("Basic HTTP authentication"));
    }
}