package com.solarmonitor.dashboard;

import com.solarmonitor.common.web.InverterResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Visão consolidada para a tela principal")
public class DashboardController {

    private final DashboardService dashboardService;
    private final InverterResolver inverterResolver;

    @GetMapping("/api/dashboard")
    @Operation(summary = "Todos os cards do dashboard numa chamada",
            description = "Instantâneos da última leitura + energia hoje/mês/total + economia, CO₂ e alertas ativos.")
    public DashboardDto dashboard(@RequestParam(name = "inverterId", required = false) Long inverterId) {
        return dashboardService.build(inverterResolver.resolve(inverterId).getId());
    }
}
