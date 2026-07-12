package com.solarmonitor.statistics;

import com.solarmonitor.common.web.InverterResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Tag(name = "Estatísticas", description = "Melhor/pior dia, picos, médias e eficiência")
public class StatisticsController {

    /** Início padrão quando 'from' é omitido — anterior a qualquer instalação real. */
    private static final LocalDate ALL_TIME_START = LocalDate.of(2000, 1, 1);

    private final StatisticsService statisticsService;
    private final InverterResolver inverterResolver;

    @GetMapping("/api/statistics")
    @Operation(summary = "Estatísticas do período",
            description = "Sem 'from'/'to', considera todo o histórico até hoje.")
    public StatisticsDto statistics(
            @RequestParam(name = "inverterId", required = false) Long inverterId,
            @RequestParam(name = "from", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var inverter = inverterResolver.resolve(inverterId);
        return statisticsService.compute(inverter.getId(),
                from != null ? from : ALL_TIME_START,
                to != null ? to : LocalDate.now());
    }
}
