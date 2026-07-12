package com.solarmonitor.energy.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Consolidação de um dia — linha dos gráficos de barras e das exportações. */
public record DailyGenerationDto(
        LocalDate date,
        BigDecimal energyKwh,
        Integer peakPowerW,
        Instant peakAt,
        Integer minPowerW,
        BigDecimal consumptionKwh,
        BigDecimal exportKwh,
        BigDecimal importKwh,
        BigDecimal selfConsumptionKwh,
        BigDecimal selfSufficiencyPct,
        BigDecimal savings,
        BigDecimal co2AvoidedKg) {
}
