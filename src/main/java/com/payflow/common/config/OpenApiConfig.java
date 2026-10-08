package com.payflow.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) configuration for PayFlow API documentation.
 * 
 * Exposes interactive API docs at /swagger-ui.html
 * OpenAPI JSON available at /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI payflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PayFlow API")
                        .description("Payment Processing and Reconciliation Platform")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("PayFlow Team")
                                .url("https://github.com/Bhagyesh-08/PayFlow"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development"),
                        new Server().url("https://api.payflow.example.com").description("Production")))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .components(new Components()
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("API Key for merchant authentication")));
    }
}
