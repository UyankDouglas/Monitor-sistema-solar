package com.solarmonitor.energy.web.dto;

import java.math.BigDecimal;

/** Consolidação de um mês. */
public record MonthlyGenerationDto(
        Short year,
        Short month,
        BigDecimal energyKwh,
        BigDecimal consumptionKwh,
        BigDecimal exportKwh,
        BigDecimal importKwh,
        BigDecimal savings,
        BigDecimal co2AvoidedKg) {
}
