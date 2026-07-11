package com.solarmonitor.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Endpoint mínimo de sanidade usado no bootstrap para confirmar que a stack
 * (Spring Boot + segurança + JSON) está no ar. Será mantido como liveness leve.
 */
@Tag(name = "Infra", description = "Sanidade e informações básicas")
@RestController
@RequestMapping("/api")
public class PingController {

    @Operation(summary = "Verifica se a API está respondendo")
    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "UP",
                "service", "monitor-solar-deye-backend",
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
