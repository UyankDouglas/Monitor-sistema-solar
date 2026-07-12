package com.solarmonitor.statistics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Estatísticas do período consultado (ou de todo o histórico).
 *
 * <p>Definições: <em>eficiência</em> é reportada como kWh/kWp (geração por
 * potência instalada) e fator de capacidade (% da energia teórica máxima do
 * período) — ambos nulos se a capacidade instalada não estiver cadastrada.</p>
 */
public record StatisticsDto(
        LocalDate from,
        LocalDate to,
        int daysWithData,
        BigDecimal totalEnergyKwh,
        BigDecimal totalSavings,
        BigDecimal totalCo2AvoidedKg,
        DayStat bestDay,
        DayStat worstDay,
        PeakStat maxPeak,
        PeakStat minPeak,
        BigDecimal avgDailyKwh,
        BigDecimal avgMonthlyKwh,
        BigDecimal kwhPerKwp,
        BigDecimal capacityFactorPct) {

    /** Um dia notável (melhor/pior) e sua geração. */
    public record DayStat(LocalDate date, BigDecimal energyKwh) {
    }

    /** Um pico de potência e quando ocorreu. */
    public record PeakStat(Integer powerW, LocalDate date, Instant at) {
    }
}
