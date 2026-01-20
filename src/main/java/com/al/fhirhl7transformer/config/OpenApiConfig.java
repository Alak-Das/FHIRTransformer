package com.al.fhirhl7transformer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for auto-generated API documentation.
 * Access Swagger UI at: /swagger-ui.html
 * Access OpenAPI JSON at: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

        @Value("${spring.application.name:HL7FHIRTransformer}")
        private String applicationName;

        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title(applicationName + " API")
                                                .version("1.0.0")
                                                .description("""
                                                                Enterprise-grade bidirectional HL7 v2.x ↔ FHIR R4 message transformation API.

                                                                ## Features
                                                                - **Bidirectional Conversion**: HL7 v2.x to FHIR R4 and vice versa
                                                                - **Sync & Async Processing**: REST APIs and RabbitMQ-based async processing
                                                                - **Batch Operations**: Parallel batch conversion with configurable concurrency
                                                                - **Multi-Tenancy**: Complete tenant isolation with RBAC
                                                                - **FHIR Validation**: Built-in FHIR R4 validation with terminology support

                                                                ## Authentication
                                                                All endpoints require Basic Authentication. Use the Authorize button to set credentials.
                                                                """)
                                                .contact(new Contact()
                                                                .name("HL7FHIRTransformer Team")
                                                                .email("support@example.com"))
                                                .license(new License()
                                                                .name("Proprietary License")
                                                                .url("https://example.com/licensing")))
                                .servers(List.of(
                                                new Server()
                                                                .url("http://localhost:8080")
                                                                .description("Local Development"),
                                                new Server()
                                                                .url("http://localhost:8090")
                                                                .description("Docker Compose")))
                                .tags(List.of(
                                                new Tag().name("Conversion")
                                                                .description("HL7 ↔ FHIR conversion endpoints"),
                                                new Tag().name("Tenant Management")
                                                                .description("Multi-tenant administration endpoints"),
                                                new Tag().name("Subscriptions")
                                                                .description("FHIR Subscription management"),
                                                new Tag().name("Transactions")
                                                                .description("Transaction status and audit endpoints")))
                                .addSecurityItem(new SecurityRequirement().addList("basicAuth"))
                                .components(new Components()
                                                .addSecuritySchemes("basicAuth", new SecurityScheme()
                                                                .type(SecurityScheme.Type.HTTP)
                                                                .scheme("basic")
                                                                .description("Basic Authentication with username and password")));
        }
}
