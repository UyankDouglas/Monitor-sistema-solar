package com.solarmonitor.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

/** Payload único do dashboard — todos os cards numa chamada. */
public record DashboardDto(
        Long inverterId,
        String inverterName,
        String inverterStatus,
        Instant lastSeenAt,
        Instant sampledAt,
        // Potências instantâneas (W)
        Integer currentPowerW,
        Integer loadPowerW,
        Integer exportPowerW,
        Integer importPowerW,
        Integer batteryPowerW,
        // Bateria
        BigDecimal batterySocPct,
        BigDecimal batteryVoltage,
        BigDecimal batteryTemperatureC,
        // Rede
        BigDecimal gridVoltageL1,
        BigDecimal gridVoltageL2,
        BigDecimal gridVoltageL3,
        BigDecimal gridFrequencyHz,
        // Inversor
        BigDecimal inverterTemperatureC,
        // Energia
        BigDecimal todayEnergyKwh,
        BigDecimal monthEnergyKwh,
        BigDecimal totalEnergyKwh,
        // Economia e CO₂
        String currency,
        BigDecimal todaySavings,
        BigDecimal monthSavings,
        BigDecimal totalSavingsEstimate,
        BigDecimal totalCo2AvoidedKgEstimate,
        // Alertas
        long activeAlerts) {
}
