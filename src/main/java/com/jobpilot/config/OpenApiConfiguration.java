package com.jobpilot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "jobpilot.openapi", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OpenApiConfiguration {

    @Bean
    OpenAPI jobPilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JobPilot API")
                        .description(
                                "Job application tracker modular monolith. "
                                        + "Authenticate with a Bearer JWT from POST /api/auth/login. "
                                        + "Public: /api/auth/**, GET /api/v1/ping, GET /actuator/health."
                        )
                        .version("0.1.0"))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt", new SecurityScheme()
                                .name("bearer-jwt")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-jwt"));
    }
}
