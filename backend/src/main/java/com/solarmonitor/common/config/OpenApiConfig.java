package com.solarmonitor.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI + esquema Bearer JWT: o botão
 * "Authorize" do Swagger UI aceita o accessToken de /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI solarMonitorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Monitor Solar Deye API")
                        .description("API de monitoramento de geração de energia solar (inversor Deye). "
                                + "Autentique-se em /api/auth/login e use o botão Authorize com o accessToken.")
                        .version("v0.0.1")
                        .contact(new Contact().name("Monitor Solar Deye"))
                        .license(new License().name("Proprietary")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
