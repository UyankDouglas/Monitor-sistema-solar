package com.solarmonitor.provider;

import com.solarmonitor.plant.domain.InverterStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Leitura completa do inversor em um instante — contrato de saída de todo
 * {@link EnergyProvider}, independente da origem (cloud, local ou simulada)
 * e desacoplado das entidades JPA.
 *
 * <p>Campos ausentes na origem ficam {@code null} e são persistidos como
 * NULL — nunca inventamos zero onde não houve medição.</p>
 *
 * @param sampledAt         instante da leitura (UTC)
 * @param status            estado do inversor reportado/deduzido
 * @param acPowerW          potência AC gerada (W)
 * @param loadPowerW        potência consumida pela casa (W)
 * @param exportPowerW      potência exportada à rede (W)
 * @param importPowerW      potência importada da rede (W)
 * @param batteryPowerW     potência da bateria (W; positivo = descarregando)
 * @param dailyEnergyKwh    energia gerada hoje (kWh)
 * @param monthlyEnergyKwh  energia gerada no mês (kWh) — nem toda origem informa
 * @param totalEnergyKwh    energia total acumulada (kWh)
 * @param mpptReadings      leituras por string MPPT (1..N)
 */
@Builder
public record EnergyReading(
        Instant sampledAt,
        InverterStatus status,
        Integer acPowerW,
        Integer loadPowerW,
        Integer exportPowerW,
        Integer importPowerW,
        Integer batteryPowerW,
        BigDecimal dailyEnergyKwh,
        BigDecimal monthlyEnergyKwh,
        BigDecimal totalEnergyKwh,
        BigDecimal gridVoltageL1,
        BigDecimal gridVoltageL2,
        BigDecimal gridVoltageL3,
        BigDecimal gridCurrentA,
        BigDecimal gridFrequencyHz,
        BigDecimal batteryVoltage,
        BigDecimal batteryCurrentA,
        BigDecimal batterySocPct,
        BigDecimal batteryTemperatureC,
        BigDecimal inverterTemperatureC,
        List<MpptStringReading> mpptReadings) {

    /**
     * Leitura de uma string MPPT.
     *
     * @param stringIndex índice 1-based da string (SUN-10K: 1 e 2)
     */
    public record MpptStringReading(
            short stringIndex,
            BigDecimal voltage,
            BigDecimal currentA,
            Integer powerW) {
    }
}
