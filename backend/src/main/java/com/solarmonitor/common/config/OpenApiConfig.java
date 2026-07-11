package com.solarmonitor.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados da documentação OpenAPI. Os esquemas de segurança (Bearer JWT)
 * serão adicionados aqui na Etapa 6.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI solarMonitorOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Monitor Solar Deye API")
                        .description("API de monitoramento de geração de energia solar (inversor Deye).")
                        .version("v0.0.1")
                        .contact(new Contact().name("Monitor Solar Deye"))
                        .license(new License().name("Proprietary")));
    }
}
