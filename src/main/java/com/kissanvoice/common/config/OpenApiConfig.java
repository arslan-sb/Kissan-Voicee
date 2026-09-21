package com.kissanvoice.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI kissanVoiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Kissan Voice Platform API")
                        .version("v1")
                        .description("""
                                Collects a spoken-Urdu agricultural corpus and publishes contributor
                                activity to downstream CRM and sales systems over an event stream.

                                Register with `POST /api/v1/contributors` to obtain a bearer token,
                                then press **Authorize** and paste it.
                                """)
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
