package com.solarmonitor.weather.web;

import com.solarmonitor.weather.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@Tag(name = "Clima", description = "Previsão do tempo × geração real (Open-Meteo)")
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/summary")
    @Operation(summary = "Clima atual + previsão × geração real (últimos 7 e próximos 7 dias)")
    public WeatherService.WeatherSummary summary() {
        return weatherService.summary();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Força a atualização imediata do clima",
            description = "Útil logo após configurar as coordenadas — o ciclo normal é horário.")
    public WeatherService.WeatherSummary refreshNow() {
        weatherService.refresh();
        return weatherService.summary();
    }
}
